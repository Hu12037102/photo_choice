package com.google.photochoice.data

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.google.photochoice.config.MediaType
import com.google.photochoice.data.model.MediaFile
import com.google.photochoice.util.MediaLoadLogger

/**
 * MediaStore 分页源。使用 keyset (dateAdded, id) 分页，单次取数由 PagingConfig 决定。
 *
 * 只支持向旧翻页（Append），配合 `PagingConfig.maxSize = MAX_SIZE_UNBOUNDED` 使用：
 * 内存中不淘汰已加载数据，因此不需要、也不实现向新回填（Prepend）。
 */
class MediaPagingSource(
    private val repository: MediaRepository,
    private val bucketId: String?,
    private val mediaType: MediaType,
    private val minVideoDurationMs: Long = 0L,
    private val maxVideoDurationMs: Long = Long.MAX_VALUE,
    private val minImageSizeBytes: Long = 0L,
    private val maxImageSizeBytes: Long = Long.MAX_VALUE
) : PagingSource<String, MediaFile>() {

    override suspend fun load(params: LoadParams<String>): LoadResult<String, MediaFile> {
        val key = params.key
        val afterDateAdded = key?.substringBefore(':')?.toLongOrNull()
        val afterId = key?.substringAfter(':')?.toLongOrNull()
        val limit = params.loadSize
        return try {
            val items = repository.loadMedia(
                bucketId = bucketId,
                mediaType = mediaType,
                limit = limit,
                afterDateAdded = afterDateAdded,
                afterId = afterId,
                minVideoDurationMs = minVideoDurationMs,
                maxVideoDurationMs = maxVideoDurationMs,
                minImageSizeBytes = minImageSizeBytes,
                maxImageSizeBytes = maxImageSizeBytes
            )
            val nextKey = if (items.size < limit) {
                null
            } else {
                val last = items.last()
                "${last.dateAdded}:${last.id}"
            }
            MediaLoadLogger.logPagingPage(
                loadType = params::class.java.simpleName,
                bucketId = bucketId,
                mediaType = mediaType,
                pageKey = key,
                nextKey = nextKey,
                items = items
            )
            LoadResult.Page(
                data = items,
                prevKey = null,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            Log.w(TAG, "load failed", e)
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<String, MediaFile>): String? = null

    companion object {
        private const val TAG = "MediaPagingSource"
    }
}
