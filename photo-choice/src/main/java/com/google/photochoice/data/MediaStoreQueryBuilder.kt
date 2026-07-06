package com.google.photochoice.data

import android.provider.MediaStore
import com.google.photochoice.config.MediaType as ConfigMediaType

/**
 * 拼装 MediaStore.Files 查询的 selection / selectionArgs，供 [MediaRepository] 与 [AlbumRepository] 共用。
 */
internal class MediaStoreQueryBuilder {

    private val selections = mutableListOf<String>()
    private val selectionArgs = mutableListOf<String>()

    fun mediaType(mediaType: ConfigMediaType): MediaStoreQueryBuilder = apply {
        when (mediaType) {
            ConfigMediaType.IMAGE -> {
                selections.add("${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?")
                selectionArgs.add(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
            }
            ConfigMediaType.VIDEO -> {
                selections.add("${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?")
                selectionArgs.add(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
            }
            ConfigMediaType.ALL -> {
                selections.add("${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)")
                selectionArgs.add(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
                selectionArgs.add(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
            }
        }
    }

    fun videoDuration(
        mediaType: ConfigMediaType,
        minVideoDurationMs: Long,
        maxVideoDurationMs: Long,
    ): MediaStoreQueryBuilder = apply {
        if (mediaType != ConfigMediaType.VIDEO &&
            !(mediaType == ConfigMediaType.ALL && maxVideoDurationMs != Long.MAX_VALUE)
        ) {
            return@apply
        }
        val durationClause =
            "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} OR " +
                "(${MediaStore.Files.FileColumns.DURATION} >= ? AND ${MediaStore.Files.FileColumns.DURATION} <= ?))"
        selections.add(durationClause)
        selectionArgs.add(minVideoDurationMs.toString())
        selectionArgs.add(maxVideoDurationMs.toString())
    }

    /**
     * 图片体积过滤（字节）。仅作用于图片行：视频/其它类型直接放行（与 [videoDuration] 对称）。
     * min<=0 且 max=Long.MAX_VALUE 时视为不过滤，不拼接子句以免白扫索引。
     */
    fun imageSize(
        mediaType: ConfigMediaType,
        minImageSizeBytes: Long,
        maxImageSizeBytes: Long,
    ): MediaStoreQueryBuilder = apply {
        if (mediaType == ConfigMediaType.VIDEO) return@apply
        if (minImageSizeBytes <= 0L && maxImageSizeBytes == Long.MAX_VALUE) return@apply
        val sizeClause =
            "(${MediaStore.Files.FileColumns.MEDIA_TYPE} != ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} OR " +
                "(${MediaStore.Files.FileColumns.SIZE} >= ? AND ${MediaStore.Files.FileColumns.SIZE} <= ?))"
        selections.add(sizeClause)
        selectionArgs.add(minImageSizeBytes.coerceAtLeast(0L).toString())
        selectionArgs.add(maxImageSizeBytes.toString())
    }

    fun excludePending(): MediaStoreQueryBuilder = apply {
        selections.add("${MediaStore.Files.FileColumns.IS_PENDING} = 0")
    }

    fun bucketNotNull(): MediaStoreQueryBuilder = apply {
        selections.add("${MediaStore.Files.FileColumns.BUCKET_ID} IS NOT NULL")
    }

    fun bucketId(bucketId: String): MediaStoreQueryBuilder = apply {
        selections.add("${MediaStore.Files.FileColumns.BUCKET_ID} = ?")
        selectionArgs.add(bucketId)
    }

    fun keysetBefore(afterDateAdded: Long, afterId: Long): MediaStoreQueryBuilder = apply {
        selections.add(
            "(${MediaStore.Files.FileColumns.DATE_ADDED} < ? OR " +
                "(${MediaStore.Files.FileColumns.DATE_ADDED} = ? AND " +
                "${MediaStore.Files.FileColumns._ID} < ?))"
        )
        selectionArgs.add(afterDateAdded.toString())
        selectionArgs.add(afterDateAdded.toString())
        selectionArgs.add(afterId.toString())
    }

    fun build(): Pair<String?, Array<String>?> {
        if (selections.isEmpty()) return null to null
        val selection = selections.joinToString(" AND ")
        val args = selectionArgs.toTypedArray()
        return selection to args
    }
}
