package com.google.photochoice.util

import android.content.Context
import android.util.Log
import com.google.photochoice.data.motion.MotionPhotoVideoResolver
import java.io.File

/**
 * 沙盒缓存清理：压缩/裁剪目录 [EXPORT_DIR]、实况图内嵌视频目录 [MOTION_DIR]、
 * 相机拍照临时目录 [CAMERA_TEMP_DIR]。
 *
 * L1：进入选择器时 [cleanExpired]（24h TTL + 实况目录容量上限）。
 * L2：宿主调用 [PhotoChoice.cleanup] → [cleanAll]。
 */
class SandboxCleaner(private val context: Context) {

    private val exportDir: File
        get() = File(context.cacheDir, EXPORT_DIR)

    private val motionDir: File
        get() = File(context.cacheDir, MOTION_DIR)

    /** 相机拍照临时文件目录（CameraHelper 写入，落库后即删；此处为异常中断的兜底清理）。 */
    private val cameraTempDir: File
        get() = File(context.cacheDir, CAMERA_TEMP_DIR)

    /**
     * 被动清理：删除超过 [maxAgeMs] 的旧文件，并对实况视频目录做容量裁剪。
     */
    fun cleanExpired(maxAgeMs: Long = DEFAULT_MAX_AGE_MS) {
        val exportRemoved = deleteExpiredFiles(exportDir, maxAgeMs)
        val motionRemoved = deleteExpiredFiles(motionDir, maxAgeMs)
        // 相机临时文件正常在落库后立即删除；此处清理的是进程被杀/拍照中断遗留的孤儿文件
        val cameraRemoved = deleteExpiredFiles(cameraTempDir, maxAgeMs)
        val trimmed = trimMotionCacheIfNeeded(motionDir)
        if (exportRemoved > 0 || motionRemoved > 0 || cameraRemoved > 0 || trimmed > 0) {
            Log.i(
                TAG,
                "cleanExpired exportRemoved=$exportRemoved motionExpired=$motionRemoved " +
                    "cameraTempRemoved=$cameraRemoved motionTrimmed=$trimmed"
            )
        }
    }

    /**
     * 主动清理：清空全部沙盒目录，并同步清空实况播放 URI 内存缓存。
     */
    fun cleanAll() {
        val exportRemoved = deleteAllFiles(exportDir)
        val motionRemoved = deleteAllFiles(motionDir)
        val cameraRemoved = deleteAllFiles(cameraTempDir)
        MotionPhotoVideoResolver.clearMemoryCache()
        Log.i(
            TAG,
            "cleanAll exportRemoved=$exportRemoved motionRemoved=$motionRemoved " +
                "cameraTempRemoved=$cameraRemoved"
        )
    }

    /**
     * 会话结束清理：清空实况内嵌视频目录（本会话预览实况图抽帧落盘的 MP4），
     * 并同步清空播放 URI 内存缓存。
     *
     * 只清实况目录、不动 [EXPORT_DIR]——压缩/裁剪产物正随 Activity Result 回传给宿主，
     * 其文件 Uri 可能仍被宿主持有，会话结束点删除会导致宿主拿到失效 Uri。
     * 实况 MP4 仅供选择器内预览播放，回传的是原图 Uri，不外泄，故可安全全删。
     */
    fun cleanMotionCache() {
        val motionRemoved = deleteAllFiles(motionDir)
        MotionPhotoVideoResolver.clearMemoryCache()
        if (motionRemoved > 0) {
            Log.i(TAG, "cleanMotionCache motionRemoved=$motionRemoved")
        }
    }

    /** 删除目录内 mtime 早于 cutoff 的普通文件，返回删除数量。 */
    private fun deleteExpiredFiles(dir: File, maxAgeMs: Long): Int {
        if (!dir.exists()) return 0
        val cutoff = System.currentTimeMillis() - maxAgeMs
        var removed = 0
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff && file.delete()) {
                removed++
            } else if (file.isFile && file.lastModified() < cutoff) {
                Log.w(TAG, "Failed to delete expired file: ${file.absolutePath}")
            }
        }
        return removed
    }

    /** 删除目录内全部普通文件，返回删除数量。 */
    private fun deleteAllFiles(dir: File): Int {
        if (!dir.exists()) return 0
        var removed = 0
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.delete()) {
                removed++
            } else if (file.isFile) {
                Log.w(TAG, "Failed to delete file: ${file.absolutePath}")
            }
        }
        return removed
    }

    /**
     * 实况内嵌视频目录容量裁剪：超出文件数或总大小时，按 mtime 从旧到新删除。
     *
     * @return 因容量上限额外删除的文件数
     */
    private fun trimMotionCacheIfNeeded(dir: File): Int {
        if (!dir.exists()) return 0
        val files = dir.listFiles()?.filter { it.isFile } ?: return 0
        if (files.isEmpty()) return 0

        val sorted = files.sortedBy { it.lastModified() }
        var totalBytes = sorted.sumOf { it.length() }
        var count = sorted.size
        var trimmed = 0

        for (file in sorted) {
            if (count <= MOTION_MAX_FILES && totalBytes <= MOTION_MAX_BYTES) break
            val size = file.length()
            if (file.delete()) {
                totalBytes -= size
                count--
                trimmed++
            } else {
                Log.w(TAG, "Failed to trim motion cache file: ${file.absolutePath}")
            }
        }
        return trimmed
    }

    companion object {
        private const val TAG = "SandboxCleaner"

        /** 压缩图 / 裁剪图目录 */
        const val EXPORT_DIR = "photo_choice"

        /** 实况图内嵌 MP4 播放缓存目录 */
        const val MOTION_DIR = "photo_choice_motion"

        /**
         * 相机拍照临时文件目录。经 FileProvider 暴露给系统相机写入，
         * 落库到公共相机目录后立即删除；须与 res/xml/photochoice_file_paths.xml 中的 path 一致。
         */
        const val CAMERA_TEMP_DIR = "photo_choice_camera"

        private const val DEFAULT_MAX_AGE_MS = 24 * 60 * 60 * 1000L

        /** 实况缓存总大小上限（约 150MB） */
        private const val MOTION_MAX_BYTES = 150L * 1024 * 1024

        /** 实况缓存文件数上限 */
        private const val MOTION_MAX_FILES = 50
    }
}
