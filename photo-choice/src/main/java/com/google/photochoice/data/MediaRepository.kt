package com.google.photochoice.data

import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.os.Bundle
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
        // API 30+ 通过 QUERY_ARG_LIMIT 下推；API 29 在 CursorWrapper 截断。
        val sortOrder =
            "${MediaStore.Files.FileColumns.DATE_ADDED} DESC, " +
                "${MediaStore.Files.FileColumns._ID} DESC"

        val externalUri = MediaStore.Files.getContentUri("external")
        queryMediaCursor(
            uri = externalUri,
            projection = projection,
            selection = selection,
            selectionArgs = selectionArgs,
            sortOrder = sortOrder,
            limit = limit
        )?.use { cursor ->
            val cols = ColumnIndex(cursor)
            while (cursor.moveToNext()) {
                mediaFiles.add(cols.toMediaFile(cursor))
            }
        }
        if (mediaFiles.isNotEmpty()) {
            applyMotionPhotoFromMediaStore(mediaFiles)
        }
        val afterKey = if (afterDateAdded != null && afterId != null) {
            "$afterDateAdded:$afterId"
        } else {
            null
        }
        // IS_MOTION_PHOTO 在 Files 统一视图中不可用；DB 标记在 applyMotionPhotoFromMediaStore 中补齐
        MediaLoadLogger.logQuery(
            bucketId = bucketId,
            mediaType = mediaType,
            limit = limit,
            afterKey = afterKey,
            items = mediaFiles
        )
        mediaFiles
    }

    /**
     * 预建用：拉取某相册(或全库)所有图片的判定所需最小列(id/size/dateAdded/displayName)。
     * 不参与分页，一次性轻查询；仅 IMAGE。
     */
    suspend fun loadImageManifestForPrebuild(
        bucketId: String? = null
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        val result = mutableListOf<MediaFile>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
        )
        val selection = StringBuilder(
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}" +
                " AND ${MediaStore.Files.FileColumns.IS_PENDING} = 0"
        )
        val args = mutableListOf<String>()
        if (!bucketId.isNullOrEmpty()) {
            selection.append(" AND ${MediaStore.Files.FileColumns.BUCKET_ID} = ?")
            args.add(bucketId)
        }
        runCatching {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection.toString(),
                if (args.isEmpty()) null else args.toTypedArray(),
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    result.add(
                        MediaFile(
                            id = id,
                            uri = MediaStoreUris.contentUriString(id, MediaFile.MediaType.IMAGE),
                            mimeType = "",
                            type = MediaFile.MediaType.IMAGE,
                            dateAdded = cursor.getLong(dateCol),
                            width = 0,
                            height = 0,
                            size = cursor.getLong(sizeCol),
                            bucketId = bucketId ?: "",
                            bucketName = "",
                            displayName = cursor.getString(nameCol) ?: ""
                        )
                    )
                }
            }
        }
        result
    }

    /** API 30+ 在 SQL 层 LIMIT，避免 keyset 翻页时在 Java 层空扫 Cursor。 */
    private fun queryMediaCursor(
        uri: android.net.Uri,
        projection: Array<String>,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String,
        limit: Int
    ): android.database.Cursor? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val queryArgs = Bundle().apply {
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                if (selection != null) {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                }
                if (selectionArgs != null) {
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                }
            }
            context.contentResolver.query(uri, projection, queryArgs, null)
        } else {
            context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
                ?.let { cursor ->
                    object : android.database.CursorWrapper(cursor) {
                        private var delivered = 0
                        override fun moveToNext(): Boolean {
                            if (delivered >= limit) return false
                            val moved = super.moveToNext()
                            if (moved) delivered++
                            return moved
                        }
                    }
                }
        }
    }

    /**
     * API 34+ 从 Images.Media 批量读取 IS_MOTION_PHOTO（纯 DB，毫秒级）。
     * XMP 嗅探由 [com.google.photochoice.data.motion.MotionPhotoListEnricher] 异步补齐。
     */
    private fun applyMotionPhotoFromMediaStore(items: MutableList<MediaFile>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val imageIds = items
            .filter { it.type == MediaFile.MediaType.IMAGE && !it.isMotionPhoto }
            .map { it.id }
        if (imageIds.isEmpty()) return

        val motionIds = MotionPhotoDetector.queryMotionIdsFromMediaStore(context, imageIds)
        if (motionIds.isEmpty()) return
        for (i in items.indices) {
            val item = items[i]
            if (item.id in motionIds) {
                items[i] = item.copy(isMotionPhoto = true)
            }
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
        val displayNameCol =
            cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)

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
                bucketName = cursor.getString(bucketNameCol) ?: "",
                displayName = cursor.getString(displayNameCol) ?: ""
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
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.DISPLAY_NAME
        )
    }
}
