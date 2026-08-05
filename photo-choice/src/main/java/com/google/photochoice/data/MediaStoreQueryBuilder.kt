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

    /**
     * 排除 0 字节与 SIZE 缺失的无效行。
     *
     * MediaStore 中会残留写入失败、传输中断、被外部删除但行未清理的条目（[SIZE] = 0 或 NULL），
     * 系统相册通常显示为损坏图。这类条目展示出来必然走进失败路径——缩略图加载不出来，
     * 选中后压缩 / 上传也会失败，属于正确性问题而非产品口味问题，故默认过滤，不做成开关。
     *
     * 与 [imageSize] 的区别：后者是宿主可配置的"多小算不该选"业务策略（默认不过滤），
     * 这里是无条件的有效性底线。
     *
     * `SIZE > 0` 在 SQL 中对 NULL 求值为 NULL（非 true），因此 0 字节与 SIZE 缺失一并排除。
     * 正常文件由 MediaProvider 扫描时写入真实长度，不会命中此条件；写入中的文件已由
     * [excludePending] 排除。
     */
    fun excludeEmptyFile(): MediaStoreQueryBuilder = apply {
        selections.add("${MediaStore.Files.FileColumns.SIZE} > 0")
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
