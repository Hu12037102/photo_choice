package com.google.photochoice.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import kotlin.random.Random

/**
 * 相机拍照落库助手。
 *
 * ## 为什么不把 MediaStore Uri 直接交给系统相机
 *
 * 旧实现走 `MediaStore.insert(IS_PENDING=1)` → `EXTRA_OUTPUT` 给相机。这条路在分区存储下必然失败：
 * MediaProvider 对 `IS_PENDING=1` 的行在 **SQL where 层**追加 `is_pending=0 OR owner_package=<caller>`
 * 过滤，**URI 授权绕不过这层**。系统相机不是该行的 owner，`openOutputStream` 直接抛
 * FileNotFoundException，照片压根没落盘——最终表现为"目录建出来了、文件不见了"。
 *
 * ## 当前实现：临时文件 → 我方自主落库
 *
 * 1. [createTempCaptureFile] 在私有 cache 目录建临时文件，经 FileProvider 暴露给相机（相机对
 *    FileProvider Uri 有完整写权限，无 owner 限制，兼容性最好）
 * 2. 相机写完后由 [saveToPublicCamera] 复制进公共相机目录 `DCIM/Camera`，此时**我方是 owner**，
 *    IS_PENDING 两阶段协议正常工作，发布后全系统可见
 * 3. 落库成功即删临时文件，失败则保留由 [SandboxCleaner] 兜底清理
 *
 * 文件命名规则：`IMG` + 时间戳后八位 + 四位随机数 + `.jpg`，例如 `IMG064001234821.jpg`。
 */
class CameraHelper(private val context: Context) {

    /**
     * 在私有 cache 目录创建拍照临时文件，并返回可交给系统相机的 FileProvider Uri。
     *
     * 返回 null 表示目录创建或文件创建失败（磁盘满 / 权限异常 / FileProvider 未正确声明），
     * 调用方据此中止拍照流程并提示用户，不 Crash。
     *
     * @return first = 临时文件（用于后续读取与成功判定），second = 交给相机的 content Uri
     */
    fun createTempCaptureFile(): Pair<File, Uri>? {
        return runCatching {
            val dir = tempDir()
            if (!dir.exists() && !dir.mkdirs()) {
                Log.w(TAG, "createTempCaptureFile: mkdirs failed, dir=${dir.absolutePath}")
                return null
            }
            // 临时文件名带随机后缀：避免同一毫秒连续拍照命中同名文件互相覆盖
            val temp = File(dir, "capture_${System.currentTimeMillis()}_${Random.nextInt(1000, 10000)}.jpg")
            if (!temp.createNewFile() && !temp.exists()) {
                Log.w(TAG, "createTempCaptureFile: createNewFile failed, file=${temp.absolutePath}")
                return null
            }
            val uri = FileProvider.getUriForFile(context, fileProviderAuthority(), temp)
            Log.i(TAG, "createTempCaptureFile ok file=${temp.absolutePath} uri=$uri")
            temp to uri
        }.onFailure {
            // FileProvider 未在宿主 manifest 合并成功时会抛 IllegalArgumentException，此处兜住不 Crash
            Log.e(TAG, "createTempCaptureFile failed", it)
        }.getOrNull()
    }

    /**
     * 将相机写好的临时文件保存进公共相机目录 `DCIM/Camera`，返回落库后的 MediaStore 行 id。
     *
     * 采用 IS_PENDING 两阶段协议：insert 时置 1（写入期间对其它应用不可见，避免半成品被扫描），
     * 字节复制完成后置 0 发布。任一环节失败都会回滚删除已插入的行，不留 0 字节孤儿记录。
     *
     * 库 minSdk = 29，分区存储恒定可用，故直接走 RELATIVE_PATH + IS_PENDING，不做版本分支。
     *
     * @param tempFile 相机已写入内容的临时文件，调用前需确保 length() > 0
     * @return 落库成功返回 MediaStore `_ID`；失败返回 null
     */
    fun saveToPublicCamera(tempFile: File): Long? {
        if (!tempFile.exists() || tempFile.length() <= 0L) {
            Log.w(TAG, "saveToPublicCamera: temp file empty or missing, file=${tempFile.absolutePath}")
            return null
        }

        val displayName = generateDisplayName()
        val nowSeconds = System.currentTimeMillis() / 1000
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, MIME_JPEG)
            put(MediaStore.Images.Media.RELATIVE_PATH, CAMERA_RELATIVE_PATH)
            // 显式写入 SIZE / 时间戳：宿主可能配置了 minImageSize 过滤，SIZE 缺失会导致
            // 新照片被列表的 SQL 体积条件筛掉；DATE_ADDED 保证新照片稳定排在列表首位
            put(MediaStore.Images.Media.SIZE, tempFile.length())
            put(MediaStore.Images.Media.DATE_ADDED, nowSeconds)
            put(MediaStore.Images.Media.DATE_MODIFIED, nowSeconds)
            // 分区存储：标记为待写入，字节复制完成前对媒体库/其它应用不可见
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = runCatching {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }.onFailure {
            Log.e(TAG, "saveToPublicCamera: insert failed", it)
        }.getOrNull() ?: run {
            Log.w(TAG, "saveToPublicCamera: insert returned null")
            return null
        }

        // 字节复制：任何失败都必须回滚，否则媒体库残留 0 字节行（系统相册显示为损坏图）
        val copied = runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                tempFile.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("openOutputStream returned null for $uri")
        }.onFailure {
            Log.e(TAG, "saveToPublicCamera: copy bytes failed, rolling back uri=$uri", it)
        }.isSuccess

        if (!copied) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }

        publish(uri)

        val id = uri.lastPathSegment?.toLongOrNull()
        Log.i(TAG, "saveToPublicCamera ok name=$displayName size=${tempFile.length()} uri=$uri id=$id")
        return id
    }

    /**
     * 删除拍照临时文件。落库成功或用户取消后调用，避免 cache 目录堆积。
     * 删除失败仅记日志——[SandboxCleaner] 会按 24h TTL 兜底清理。
     */
    fun deleteTempFile(tempFile: File) {
        if (!tempFile.exists()) return
        if (!tempFile.delete()) {
            Log.w(TAG, "deleteTempFile failed, file=${tempFile.absolutePath}")
        }
    }

    /**
     * 清除 IS_PENDING 使照片正式对媒体库可见（分区存储两阶段协议的第二阶段）。
     */
    private fun publish(uri: Uri) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
        }
        runCatching {
            context.contentResolver.update(uri, values, null, null)
        }.onFailure {
            // 更新失败不致命：照片仍处待发布态，主流程不 Crash；下次扫描或重启后系统会兜底
            Log.e(TAG, "publish failed, photo stays pending, uri=$uri", it)
        }
    }

    /**
     * 生成文件名：`IMG` + 时间戳后八位 + 四位随机数 + `.jpg`。
     *
     * 时间戳后八位保证时间近似有序，四位随机数消除同毫秒连拍碰撞。
     * 即便极端情况下重名，MediaStore 也会自动追加 `(1)` 后缀，不会覆盖已有文件。
     */
    private fun generateDisplayName(): String {
        val timestampTail = (System.currentTimeMillis() % 100_000_000L)
            .toString()
            .padStart(8, '0')
        val random = Random.nextInt(1000, 10000)
        return "$NAME_PREFIX$timestampTail$random.jpg"
    }

    /** 拍照临时文件目录，与 [SandboxCleaner.CAMERA_TEMP_DIR] 保持一致以便被统一清理。 */
    private fun tempDir(): File = File(context.cacheDir, SandboxCleaner.CAMERA_TEMP_DIR)

    /**
     * FileProvider authority。与 AndroidManifest 中的 `${applicationId}.photochoice.fileprovider`
     * 占位符对应——用宿主 applicationId 拼接，保证多个集成方之间 authority 不冲突。
     *
     * 注意用 [Context.getPackageName] 而非库自身的 BuildConfig.APPLICATION_ID：后者在库模块里是
     * 库的包名，与宿主 manifest 合并后的实际 authority 对不上。
     */
    private fun fileProviderAuthority(): String = "${context.packageName}$FILE_PROVIDER_SUFFIX"

    companion object {
        private const val TAG = "PhotoChoice/Camera"

        private const val MIME_JPEG = "image/jpeg"

        /** 文件名前缀，与系统相机惯例保持一致。 */
        private const val NAME_PREFIX = "IMG"

        /** 公共相机目录名。 */
        private const val CAMERA_DIR_NAME = "Camera"

        /** API 29+ 用的相对路径：`DCIM/Camera`，即系统"相机"相册。 */
        private val CAMERA_RELATIVE_PATH =
            Environment.DIRECTORY_DCIM + File.separator + CAMERA_DIR_NAME

        /** FileProvider authority 后缀，须与 AndroidManifest 声明一致。 */
        private const val FILE_PROVIDER_SUFFIX = ".photochoice.fileprovider"
    }
}
