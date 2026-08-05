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
        maxVideoDurationMs: Long = Long.MAX_VALUE,
        minImageSizeBytes: Long = 0L,
        maxImageSizeBytes: Long = Long.MAX_VALUE
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        val mediaFiles = mutableListOf<MediaFile>()
        val projection = PROJECTION

        val query = MediaStoreQueryBuilder()
            .mediaType(mediaType)
            .videoDuration(mediaType, minVideoDurationMs, maxVideoDurationMs)
            .imageSize(mediaType, minImageSizeBytes, maxImageSizeBytes)
            .excludePending()
            .excludeEmptyFile()
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
     * 计算当前可见媒体集合的签名（Android 14 部分授权场景专用）。
     *
     * 用途：部分授权下重新拉起系统照片选择器后，权限授予态不变，只能从内容侧判断
     * 用户是否真的改动了可选集合——签名一致则跳过刷新，避免无意义的 Paging refresh
     * 引起首屏 rebind 闪烁（见 ViewModel.reconcileMediaVisibility）。
     *
     * 实现：只 SELECT _ID 一列做序敏感聚合（count + 逐行混合 id），部分授权下可见
     * 集合通常只有几十行，毫秒级完成。过滤条件与网格查询完全同口径——签名衡量的是
     * "网格能看到什么"，口径不一致会把网格根本不展示的行的变化也算成变化。
     * 撤销授权（集合变小）同样会改变签名，一并覆盖。
     *
     * @return 签名值；查询失败返回 null，调用方应放弃本次比对而非视为空集合
     */
    suspend fun computeVisibilitySignature(
        mediaType: ConfigMediaType,
        minVideoDurationMs: Long,
        maxVideoDurationMs: Long,
        minImageSizeBytes: Long,
        maxImageSizeBytes: Long
    ): Long? = withContext(Dispatchers.IO) {
        val (selection, selectionArgs) = MediaStoreQueryBuilder()
            .mediaType(mediaType)
            .videoDuration(mediaType, minVideoDurationMs, maxVideoDurationMs)
            .imageSize(mediaType, minImageSizeBytes, maxImageSizeBytes)
            .excludePending()
            .excludeEmptyFile()
            .build()
        runCatching {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(MediaStore.Files.FileColumns._ID),
                selection,
                selectionArgs,
                "${MediaStore.Files.FileColumns._ID} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                // 31 进制滚动哈希：对集合内容与行数都敏感；固定 _ID 排序使签名与查询顺序无关
                var signature = cursor.count.toLong()
                while (cursor.moveToNext()) {
                    signature = signature * 31 + cursor.getLong(idCol)
                }
                signature
            }
        }.onFailure {
            android.util.Log.w("PhotoChoice/Media", "computeVisibilitySignature failed", it)
        }.getOrNull()
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
                " AND ${MediaStore.Files.FileColumns.IS_PENDING} = 0" +
                // 与列表口径一致：0 字节行嗅探必然失败，跳过可省一次无谓的 IO
                " AND ${MediaStore.Files.FileColumns.SIZE} > 0"
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

    /**
     * 按 MediaStore 行 id 查询单条媒体，列与 [loadMedia] 完全一致。
     *
     * 用于相机拍照落库后即时取回该条目——列表刷新是异步的，拿不到刚拍的 MediaFile 就无法
     * 自动选中。此处直接按 id 精确查询，绕过列表分页与过滤条件的时序依赖。
     *
     * 注意：**刻意不带 mediaType / 体积 / 时长等过滤条件**。拍照产物是用户主动创建的，
     * 语义上必须能被选中；若套用宿主配置的过滤条件（如 minImageSize），会出现"拍了照却选不上"
     * 的断裂路径。过滤只作用于浏览既有媒体，不作用于本次拍摄结果。
     *
     * @return 查到返回 MediaFile；行不存在或已被删除返回 null
     */
    suspend fun loadMediaById(id: Long): MediaFile? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                PROJECTION,
                "${MediaStore.Files.FileColumns._ID} = ?",
                arrayOf(id.toString()),
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                ColumnIndex(cursor).toMediaFile(cursor)
            }
        }.onFailure {
            android.util.Log.w("PhotoChoice/Media", "loadMediaById failed, id=$id", it)
        }.getOrNull()
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
