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

/**
 * 实况图 / Motion Photo 检测。
 *
 * 列表分页路径仅做 MediaStore 批量查询（API 34+），失败时原样返回，不阻塞列表。
 * XMP 嗅探仅在预览长按等单条场景调用，避免拖垮首屏加载。
 */
object MotionPhotoDetector {

    private const val TAG = "MotionPhotoDetector"
    private const val QUERY_BATCH = 100
    /** 分页后快速 XMP 头筛查条数上限（仅读文件头，不阻塞整页）。 */
    private const val QUICK_SNIFF_LIMIT = 48
    private const val QUICK_SNIFF_PARALLEL = 8

    /** [android.provider.MediaStore.MediaColumns.IS_MOTION_PHOTO]（API 34+） */
    private const val COLUMN_IS_MOTION_PHOTO = "is_motion_photo"

    private val cache = LruCache<Long, Boolean>(512)

    /**
     * 为分页结果补充 [MediaFile.isMotionPhoto]；任何异常均降级为原列表。
     */
    suspend fun enrichImages(context: Context, items: List<MediaFile>): List<MediaFile> =
        withContext(Dispatchers.IO) {
            if (items.isEmpty()) return@withContext items
            val imageIds = items
                .filter { it.type == MediaFile.MediaType.IMAGE }
                .map { it.id }
            if (imageIds.isEmpty()) return@withContext items

            val motionIds = mutableSetOf<Long>()
            motionIds.addAll(
                runCatching { queryFromMediaStore(context, imageIds) }
                    .getOrElse { error ->
                        Log.w(TAG, "MediaStore motion photo query failed, skip", error)
                        emptySet()
                    }
            )
            motionIds.addAll(quickSniffMotionIds(context, items, motionIds))

            items.map { file ->
                if (file.id in motionIds) file.copy(isMotionPhoto = true) else file
            }
        }

    /**
     * 单条检测（预览页长按播放前、或列表未命中 MediaStore 时补检）。
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
                MotionPhotoXmpSniffer.isMotionPhoto(context, Uri.parse(media.uri))
            }.getOrDefault(false)
            cache.put(media.id, fromXmp)
            fromXmp
        }

    fun isMotionPhotoCached(media: MediaFile): Boolean {
        if (media.type != MediaFile.MediaType.IMAGE) return false
        if (media.isMotionPhoto) return true
        return cache.get(media.id) == true
    }

    private suspend fun quickSniffMotionIds(
        context: Context,
        items: List<MediaFile>,
        alreadyKnown: Set<Long>
    ): Set<Long> = coroutineScope {
        val candidates = items
            .filter { it.type == MediaFile.MediaType.IMAGE && it.id !in alreadyKnown }
            .take(QUICK_SNIFF_LIMIT)
        if (candidates.isEmpty()) return@coroutineScope emptySet()

        candidates.chunked(QUICK_SNIFF_PARALLEL).flatMap { chunk ->
            chunk.map { file ->
                async(Dispatchers.IO) {
                    val uri = Uri.parse(file.uri)
                    if (MotionPhotoXmpSniffer.isMotionPhotoQuick(context, uri)) {
                        cache.put(file.id, true)
                        file.id
                    } else {
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }.toSet()
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
