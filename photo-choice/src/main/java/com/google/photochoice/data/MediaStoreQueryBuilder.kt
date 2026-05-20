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
