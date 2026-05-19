package com.google.photochoice.data

import android.content.Context
import android.provider.MediaStore
import com.google.photochoice.config.MediaType as ConfigMediaType
import com.google.photochoice.data.model.Album
import com.google.photochoice.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 相册仓库：聚合 bucket，封面取每个目录 DATE_ADDED 降序首条。
 *
 * 单次查询遍历 Cursor 同时收集 bucket 数量与最新封面，避免 N 次子查询。
 */
class AlbumRepository(private val context: Context) {

    private val cameraBucketAliases: Set<String> by lazy {
        context.resources.getStringArray(R.array.photochoice_camera_bucket_aliases)
            .map { it.trim().lowercase(Locale.ROOT) }
            .toSet()
    }

    suspend fun loadAlbums(
        mediaType: ConfigMediaType = ConfigMediaType.IMAGE,
        minVideoDurationMs: Long = 0L,
        maxVideoDurationMs: Long = Long.MAX_VALUE
    ): List<Album> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME
        )

        val selections = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

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

        // 视频时长过滤（与 MediaRepository 一致）
        if (mediaType == ConfigMediaType.VIDEO ||
            (mediaType == ConfigMediaType.ALL && maxVideoDurationMs != Long.MAX_VALUE)
        ) {
            val durationClause =
                "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} OR " +
                    "(${MediaStore.Files.FileColumns.DURATION} >= ? AND ${MediaStore.Files.FileColumns.DURATION} <= ?))"
            selections.add(durationClause)
            selectionArgs.add(minVideoDurationMs.toString())
            selectionArgs.add(maxVideoDurationMs.toString())
        }

        selections.add("${MediaStore.Files.FileColumns.BUCKET_ID} IS NOT NULL")
        selections.add("${MediaStore.Files.FileColumns.IS_PENDING} = 0")

        val selection = selections.joinToString(" AND ")
        val sortOrder =
            "${MediaStore.Files.FileColumns.DATE_ADDED} DESC, ${MediaStore.Files.FileColumns._ID} DESC"

        val externalUri = MediaStore.Files.getContentUri("external")
        val bucketMap = LinkedHashMap<String, BucketInfo>()

        context.contentResolver.query(
            externalUri,
            projection,
            selection,
            selectionArgs.toTypedArray(),
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val mediaTypeCol =
                cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val bucketIdCol =
                cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
            val bucketNameCol =
                cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val bucketId = cursor.getString(bucketIdCol) ?: continue
                val bucketName = cursor.getString(bucketNameCol) ?: ""
                val id = cursor.getLong(idCol)
                val mediaType = cursor.getInt(mediaTypeCol)
                val date = cursor.getLong(dateCol)
                val info = bucketMap[bucketId]
                if (info == null) {
                    bucketMap[bucketId] = BucketInfo(
                        name = bucketName,
                        count = 1,
                        coverId = id,
                        coverMediaType = mediaType,
                        latestDate = date
                    )
                } else {
                    info.count++
                    if (date > info.latestDate) {
                        info.latestDate = date
                        info.coverId = id
                        info.coverMediaType = mediaType
                    }
                }
            }
        }

        bucketMap
            .map { (bucketId, info) ->
                val coverUri = MediaStoreUris.contentUriString(
                    info.coverId,
                    info.coverMediaType
                )
                Album(
                    id = bucketId,
                    bucketId = bucketId,
                    displayName = info.name,
                    coverUri = coverUri,
                    mediaCount = info.count,
                    latestDateAdded = info.latestDate
                )
            }
            .sortedWith(albumOrder())
    }

    /** 系统相机目录置顶；其余按最新时间倒序。 */
    private fun albumOrder(): Comparator<Album> {
        return Comparator { a, b ->
            val aCamera = isCameraAlbum(a.displayName)
            val bCamera = isCameraAlbum(b.displayName)
            when {
                aCamera && !bCamera -> -1
                !aCamera && bCamera -> 1
                else -> b.latestDateAdded.compareTo(a.latestDateAdded)
            }
        }
    }

    private fun isCameraAlbum(name: String): Boolean {
        val normalized = name.trim().lowercase(Locale.ROOT)
        return normalized in cameraBucketAliases
    }

    private data class BucketInfo(
        val name: String,
        var count: Int,
        var coverId: Long,
        var coverMediaType: Int,
        var latestDate: Long
    )
}
