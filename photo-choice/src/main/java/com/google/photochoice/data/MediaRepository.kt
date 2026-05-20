package com.google.photochoice.data

import android.content.Context
import android.provider.MediaStore
import com.google.photochoice.config.MediaType as ConfigMediaType
import com.google.photochoice.data.model.MediaFile
import com.google.photochoice.data.motion.MotionPhotoDetector
import com.google.photochoice.util.MediaLoadLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MediaStore 媒体仓库，仅查询公共目录。
 *
 * - 仅 SELECT 必要列以减少 IO
 * - keyset 分页（DATE_ADDED, _ID）保证强一致游标
 * - 通过 ContentResolver.QUERY_ARG_* 使用非过时查询 API
 */
class MediaRepository(private val context: Context) {

    suspend fun loadMedia(
        bucketId: String? = null,
        mediaType: ConfigMediaType = ConfigMediaType.IMAGE,
        limit: Int = 100,
        afterDateAdded: Long? = null,
        afterId: Long? = null,
        minVideoDurationMs: Long = 0L,
        maxVideoDurationMs: Long = Long.MAX_VALUE
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        val mediaFiles = mutableListOf<MediaFile>()
        val projection = PROJECTION

        val query = MediaStoreQueryBuilder()
            .mediaType(mediaType)
            .videoDuration(mediaType, minVideoDurationMs, maxVideoDurationMs)
            .excludePending()
        if (!bucketId.isNullOrEmpty()) {
            query.bucketId(bucketId)
        }
        if (afterDateAdded != null && afterId != null) {
            query.keysetBefore(afterDateAdded, afterId)
        }
        val (selection, selectionArgs) = query.build()
        // 勿在 sortOrder 中拼接 LIMIT：MediaStore 在多数机型上会返回空 Cursor。
        // 分页条数在 Cursor 遍历阶段截断。
        val sortOrder =
            "${MediaStore.Files.FileColumns.DATE_ADDED} DESC, " +
                "${MediaStore.Files.FileColumns._ID} DESC"

        val externalUri = MediaStore.Files.getContentUri("external")
        context.contentResolver.query(
            externalUri,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val cols = ColumnIndex(cursor)
            var loaded = 0
            while (cursor.moveToNext() && loaded < limit) {
                mediaFiles.add(cols.toMediaFile(cursor))
                loaded++
            }
        }
        val afterKey = if (afterDateAdded != null && afterId != null) {
            "$afterDateAdded:$afterId"
        } else {
            null
        }
        val enriched = runCatching {
            MotionPhotoDetector.enrichImages(context, mediaFiles)
        }.getOrElse { mediaFiles }
        MediaLoadLogger.logQuery(
            bucketId = bucketId,
            mediaType = mediaType,
            limit = limit,
            afterKey = afterKey,
            items = enriched
        )
        enriched
    }

    suspend fun getMediaById(id: Long): MediaFile? = withContext(Dispatchers.IO) {
        val externalUri = MediaStore.Files.getContentUri("external")
        context.contentResolver.query(
            externalUri,
            PROJECTION,
            "${MediaStore.Files.FileColumns._ID} = ?",
            arrayOf(id.toString()),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val file = ColumnIndex(cursor).toMediaFile(cursor)
                MotionPhotoDetector.enrichImages(context, listOf(file)).firstOrNull()
            } else null
        }
    }

    private class ColumnIndex(cursor: android.database.Cursor) {
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
        val mediaTypeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
        val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
        val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
        val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
        val bucketNameCol =
            cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)

        fun toMediaFile(cursor: android.database.Cursor): MediaFile {
            val type = when (cursor.getInt(mediaTypeCol)) {
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaFile.MediaType.VIDEO
                else -> MediaFile.MediaType.IMAGE
            }
            val id = cursor.getLong(idCol)
            val contentUri = MediaStoreUris.contentUriString(id, type)
            return MediaFile(
                id = id,
                uri = contentUri,
                mimeType = cursor.getString(mimeCol) ?: "",
                type = type,
                dateAdded = cursor.getLong(dateCol),
                width = cursor.getInt(widthCol),
                height = cursor.getInt(heightCol),
                size = cursor.getLong(sizeCol),
                duration = cursor.getLong(durationCol),
                bucketId = cursor.getString(bucketIdCol) ?: "",
                bucketName = cursor.getString(bucketNameCol) ?: ""
            )
        }
    }

    companion object {
        private val PROJECTION = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME
        )
    }
}
