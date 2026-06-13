package com.google.photochoice.data

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
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
        if (mediaFiles.isNotEmpty()) {
            enrichMotionPhoto(mediaFiles)
        }
        val afterKey = if (afterDateAdded != null && afterId != null) {
            "$afterDateAdded:$afterId"
        } else {
            null
        }
        // IS_MOTION_PHOTO 在 Files 统一视图中不可用，通过 enrichMotionPhoto 单独检测
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
     * 为图片补充实况图标记。两步检测：
     * 1. API 34+ 查 [MediaStore.Images.Media] 的 IS_MOTION_PHOTO（纯 DB 读，零 IO）
     * 2. XMP 快速嗅探兜底（OEM 未写标记、API < 34）
     */
    private suspend fun enrichMotionPhoto(items: MutableList<MediaFile>) {
        val motionIds = mutableSetOf<Long>()

        // 第 1 步：Images.Media 查询（API 34+，纯 DB 读，Files 视图无此列）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val imageIds = items
                .filter { it.type == MediaFile.MediaType.IMAGE }
                .map { it.id }
            if (imageIds.isNotEmpty()) {
                imageIds.chunked(QUERY_BATCH).forEach { chunk ->
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
                                motionIds.add(cursor.getLong(idCol))
                            }
                        }
                    }.onFailure { e ->
                        Log.w(TAG, "IS_MOTION_PHOTO query failed (${chunk.size} ids)", e)
                    }
                }
            }
        }

        // 第 2 步：XMP 嗅探兜底（跳过 Step 1 已确认 + 缓存命中，单页上限 100 条）
        val xmpIds = MotionPhotoDetector.quickSniffBatch(context, items, motionIds)
        motionIds.addAll(xmpIds)

        if (motionIds.isNotEmpty()) {
            for (i in items.indices) {
                val item = items[i]
                if (item.id in motionIds) {
                    items[i] = item.copy(isMotionPhoto = true)
                }
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
        private const val TAG = "MediaRepository"
        private const val QUERY_BATCH = 100

        /** [android.provider.MediaStore.MediaColumns.IS_MOTION_PHOTO]（API 34+） */
        private const val COLUMN_IS_MOTION_PHOTO = "is_motion_photo"

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
