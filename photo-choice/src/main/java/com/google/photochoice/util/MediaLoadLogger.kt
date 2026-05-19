package com.google.photochoice.util

import android.util.Log
import com.google.photochoice.BuildConfig
import com.google.photochoice.config.MediaType
import com.google.photochoice.data.model.MediaFile

/**
 * 媒体列表加载调试日志（仅 Debug 构建输出）。
 */
object MediaLoadLogger {

    private const val TAG = "PhotoChoice/Media"
    private const val MAX_DETAIL_LINES = 30

    fun logQuery(
        bucketId: String?,
        mediaType: MediaType,
        limit: Int,
        afterKey: String?,
        items: List<MediaFile>
    ) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            TAG,
            "MediaRepository.loadMedia bucket=$bucketId type=$mediaType limit=$limit " +
                "afterKey=$afterKey -> count=${items.size}"
        )
        logItems("query", items)
    }

    fun logPagingPage(
        loadType: String,
        bucketId: String?,
        mediaType: MediaType,
        pageKey: String?,
        nextKey: String?,
        items: List<MediaFile>
    ) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            TAG,
            "MediaPagingSource.load type=$loadType bucket=$bucketId mediaType=$mediaType " +
                "pageKey=$pageKey nextKey=$nextKey -> pageSize=${items.size}"
        )
        logItems("page", items)
    }

    fun logGridSubmit(itemCount: Int, snapshot: List<MediaFile>) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            TAG,
            "MediaGrid submitData adapterItemCount=$itemCount snapshotSize=${snapshot.size}"
        )
        logItems("grid-snapshot", snapshot)
    }

    fun logGridLoadState(
        refresh: String,
        append: String,
        itemCount: Int,
        error: Throwable?
    ) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            TAG,
            "MediaGrid loadState refresh=$refresh append=$append itemCount=$itemCount " +
                "error=${error?.message ?: "none"}"
        )
        error?.let { Log.w(TAG, "MediaGrid load error", it) }
    }

    private fun logItems(label: String, items: List<MediaFile>) {
        if (items.isEmpty()) {
            Log.d(TAG, "  [$label] (empty)")
            return
        }
        val limit = minOf(items.size, MAX_DETAIL_LINES)
        for (i in 0 until limit) {
            val item = items[i]
            Log.d(
                TAG,
                "  [$label][$i] id=${item.id} type=${item.type} uri=${item.uri} " +
                    "bucket=${item.bucketName}(${item.bucketId}) dateAdded=${item.dateAdded} " +
                    "size=${item.size} mime=${item.mimeType}"
            )
        }
        if (items.size > MAX_DETAIL_LINES) {
            Log.d(TAG, "  [$label] ... ${items.size - MAX_DETAIL_LINES} more items omitted")
        }
    }
}
