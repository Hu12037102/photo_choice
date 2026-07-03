package com.google.photochoice.data.motion

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.collection.LruCache
import com.google.photochoice.data.model.MediaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

/**
 * 实况图 / Motion Photo 检测。
 *
 * 列表分页路径已通过 MediaStore PROJECTION 直接查询 IS_MOTION_PHOTO（API 34+），
 * 此处仅保留单条检测供预览页长按播放前确认。
 */
object MotionPhotoDetector {

    private const val TAG = "MotionPhotoDetector"
    private const val QUERY_BATCH = 100
    private const val QUICK_SNIFF_PARALLEL = 8
    /** 负缓存容量：覆盖常见相册规模，避免深滚后首部结果被挤出。 */
    private const val SNIFF_CACHE_SIZE = 2048

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
            fromXmp
        }

    fun isMotionPhotoCached(media: MediaFile): Boolean {
        if (media.type != MediaFile.MediaType.IMAGE) return false
        if (media.isMotionPhoto) return true
        return cache.get(media.id) == true
    }

    /** 是否已有检测结果（含阴性），用于跳过重复嗅探。 */
    fun hasCachedResult(id: Long): Boolean = cache.get(id) != null

    /**
     * API 34+ 批量查询 MediaStore IS_MOTION_PHOTO 标记。
     */
    fun queryMotionIdsFromMediaStore(context: Context, ids: List<Long>): Set<Long> =
        queryFromMediaStore(context, ids)

    /**
     * 快速 XMP 批量嗅探。对 [items] 全量分块处理，不截断尾部条目。
     */
    suspend fun quickSniffBatch(
        context: Context,
        items: List<MediaFile>,
        alreadyKnown: Set<Long> = emptySet(),
        parallelism: Int? = null
    ): Set<Long> = coroutineScope {
        val candidates = items
            .filter { it.type == MediaFile.MediaType.IMAGE && it.id !in alreadyKnown }
            .filter { cache.get(it.id) == null }
        if (candidates.isEmpty()) return@coroutineScope emptySet()

        val parallel = parallelism ?: QUICK_SNIFF_PARALLEL
        val detected = mutableSetOf<Long>()
        for (chunk in candidates.chunked(QUERY_BATCH)) {
            chunk.chunked(parallel).forEach { group ->
                group.map { file ->
                    async(Dispatchers.IO) {
                        val uri = Uri.parse(file.uri)
                        val isMotion = MotionPhotoXmpSniffer.isMotionPhotoQuick(context, uri)
                        cache.put(file.id, isMotion)
                        if (isMotion) file.id else null
                    }
                }.awaitAll().filterNotNull().let { detected.addAll(it) }
            }
        }
        detected
    }

    private fun queryFromMediaStore(context: Context, ids: List<Long>): Set<Long> {
        if (ids.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return emptySet()
        }
        val result = mutableSetOf<Long>()
        ids.chunked(QUERY_BATCH).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            val selection =
                "${MediaStore.Images.Media._ID} IN ($placeholders) AND " +
                    "$COLUMN_IS_MOTION_PHOTO = 1"
            runCatching {
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media._ID),
                    selection,
                    chunk.map { it.toString() }.toTypedArray(),
                    null
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        result.add(id)
                        cache.put(id, true)
                    }
                }
            }.onFailure { error ->
                Log.w(TAG, "chunk query failed (${chunk.size} ids)", error)
            }
        }
        return result
    }
}
