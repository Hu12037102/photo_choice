package com.google.photochoice.data.motion

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.collection.LruCache
import com.google.photochoice.data.model.MediaFile
import com.google.photochoice.util.PhotoChoiceLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import java.util.concurrent.ConcurrentHashMap

/**
 * 实况图 / Motion Photo 检测。
 *
 * 列表：分页 load 仅同步 MediaStore 字段（[MediaRepository]）；XMP 由
 * [MotionPhotoListEnricher] 异步处理，逐条回调刷新角标，不阻塞列表加载。
 */
object MotionPhotoDetector {

    private const val TAG = "MotionPhotoDetector"
    private const val QUERY_BATCH = 100
    private const val QUICK_SNIFF_PARALLEL = 8
    /** 负缓存容量：长列表深滚仍需保留首部/中部检测结果。 */
    private const val SNIFF_CACHE_SIZE = 8192

    /** [android.provider.MediaStore.MediaColumns.IS_MOTION_PHOTO]（API 34+） */
    private const val COLUMN_IS_MOTION_PHOTO = "is_motion_photo"

    private val cache = LruCache<Long, Boolean>(SNIFF_CACHE_SIZE)

    /**
     * 单条检测（预览页长按播放前确认，含 XMP 嗅探兜底 API < 34 设备）。
     */
    suspend fun detectSingle(context: Context, media: MediaFile): Boolean =
        withContext(Dispatchers.IO) {
            if (media.type != MediaFile.MediaType.IMAGE) return@withContext false
            if (media.isMotionPhoto) return@withContext true
            cache.get(media.id)?.let { return@withContext it }

            val fromStore = queryFromMediaStore(context, listOf(media.id)).contains(media.id)
            if (fromStore) {
                cache.put(media.id, true)
                return@withContext true
            }

            val fromXmp = runCatching {
                MotionPhotoXmpSniffer.isMotionPhoto(context, media.uri.toUri())
            }.getOrDefault(false)
            cache.put(media.id, fromXmp)
            MotionPhotoIndexStore.put(media, fromXmp)  // 双写 L2
            fromXmp
        }

    fun isMotionPhotoCached(media: MediaFile): Boolean {
        if (media.type != MediaFile.MediaType.IMAGE) return false
        if (media.isMotionPhoto) return true
        return cache.get(media.id) == true
    }

    /** 是否已有检测结果（含阴性），用于跳过重复嗅探。 */
    fun hasCachedResult(id: Long): Boolean = cache.get(id) != null

    /** L1 内存缓存三态：null=未缓存，true/false=已判定。供级联 L1 与预建写穿。 */
    fun memoryResult(id: Long): Boolean? = cache.get(id)

    /**
     * API 34+ 批量查询 MediaStore IS_MOTION_PHOTO 标记。
     */
    fun queryMotionIdsFromMediaStore(context: Context, ids: List<Long>): Set<Long> =
        queryFromMediaStore(context, ids)

    /**
     * 相册打开时一次性预热：拉取 bucket（或全库）内所有 IS_MOTION_PHOTO=1。
     * bind 时 O(1) 命中缓存，无需等 XMP（毫秒级 DB 扫描）。
     *
     * @return 预热到的实况图 id 集合
     */
    fun warmAlbumFromMediaStore(context: Context, bucketId: String?): Set<Long> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return emptySet()
        }
        val result = mutableSetOf<Long>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            COLUMN_IS_MOTION_PHOTO,
        )
        val selection: String?
        val selectionArgs: Array<String>?
        if (bucketId.isNullOrEmpty()) {
            selection = null
            selectionArgs = null
        } else {
            selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
            selectionArgs = arrayOf(bucketId)
        }
        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val motionCol = cursor.getColumnIndex(COLUMN_IS_MOTION_PHOTO)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val isMotion = motionCol >= 0 && cursor.getInt(motionCol) == 1
                    cache.put(id, isMotion)
                    if (isMotion) {
                        result.add(id)
                    }
                }
            }
        }.onFailure { error ->
            Log.w(TAG, "warmAlbumFromMediaStore failed bucket=$bucketId", error)
        }
        if (result.isNotEmpty()) {
            PhotoChoiceLog.d(TAG) {
                "warmAlbumFromMediaStore bucket=$bucketId motionCount=${result.size}"
            }
        }
        return result
    }

    /**
     * 快速 XMP 批量嗅探。
     *
     * 并发模型：用 [Semaphore] 限制真正的在飞嗅探数为 parallel，避免旧实现中"子批串行 await"
     * 把有效并发架空成固定值——视口紧急通道(高 parallel)由此真正获得快滑追赶所需的吞吐。
     * 每条嗅探完成即回调，不必等整批，角标刷新更及时。
     *
     * @param onEachResult 每条嗅探完成即回调 (id, isMotion)，用于角标逐条刷新/校正
     */
    suspend fun quickSniffBatch(
        context: Context,
        items: List<MediaFile>,
        alreadyKnown: Set<Long> = emptySet(),
        parallelism: Int? = null,
        onEachResult: suspend (Long, Boolean) -> Unit = { _, _ -> },
    ): Set<Long> = coroutineScope {
        val candidates = items
            .filter { it.type == MediaFile.MediaType.IMAGE && it.id !in alreadyKnown }
            .filter { cache.get(it.id) == null }
            .filter { MotionPhotoIndexStore.query(it) == IndexResult.UNKNOWN }
        if (candidates.isEmpty()) return@coroutineScope emptySet()

        val parallel = (parallelism ?: QUICK_SNIFF_PARALLEL).coerceAtLeast(1)
        // Semaphore 控制真实并发上限；detected 跨多个 IO 协程写入，需线程安全集合
        val gate = Semaphore(parallel)
        val detected = ConcurrentHashMap.newKeySet<Long>()
        // 外层按 QUERY_BATCH 分批，仅为限制同时创建的协程对象数（在飞并发由 gate 收口）
        for (chunk in candidates.chunked(QUERY_BATCH)) {
            chunk.map { file ->
                async(Dispatchers.IO) {
                    // 许可证只圈住昂贵的文件 I/O：拿到结果即释放，主线程回调不占并发名额
                    val isMotion = gate.withPermit {
                        val uri = file.uri.toUri()
                        val motion = MotionPhotoXmpSniffer.isMotionPhotoQuick(context, uri)
                        cache.put(file.id, motion)
                        MotionPhotoIndexStore.put(file, motion)  // 双写 L2
                        if (motion) detected.add(file.id)
                        motion
                    }
                    onEachResult(file.id, isMotion)  // 每条完成即回调，无需等整批
                }
            }.awaitAll()
        }
        detected
    }

    /**
     * 读取 IS_MOTION_PHOTO 列值（不用 SQL 过滤，兼容 OEM 布尔存储差异；阴性也写入缓存）。
     */
    private fun queryFromMediaStore(context: Context, ids: List<Long>): Set<Long> {
        if (ids.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return emptySet()
        }
        val result = mutableSetOf<Long>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            COLUMN_IS_MOTION_PHOTO,
        )
        ids.chunked(QUERY_BATCH).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            val selection = "${MediaStore.Images.Media._ID} IN ($placeholders)"
            runCatching {
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    chunk.map { it.toString() }.toTypedArray(),
                    null
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val motionCol = cursor.getColumnIndex(COLUMN_IS_MOTION_PHOTO)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val isMotion = motionCol >= 0 && cursor.getInt(motionCol) == 1
                        cache.put(id, isMotion)
                        if (isMotion) {
                            result.add(id)
                        }
                    }
                }
            }.onFailure { error ->
                Log.w(TAG, "MediaStore motion query failed (${chunk.size} ids)", error)
            }
        }
        return result
    }
}
