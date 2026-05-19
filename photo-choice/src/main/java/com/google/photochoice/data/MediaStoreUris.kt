package com.google.photochoice.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import com.google.photochoice.data.model.MediaFile

/**
 * 将 MediaStore 行 ID 转为可打开的 content URI。
 *
 * [MediaStore.Files] 的 `content://media/external/file/{id}` 在多数机型上无法被
 * ContentResolver / Glide 直接打开（ENOENT）。应按媒体类型使用 Images / Video 集合 URI。
 */
object MediaStoreUris {

    fun contentUri(id: Long, type: MediaFile.MediaType): Uri =
        ContentUris.withAppendedId(collectionUri(type), id)

    fun contentUriString(id: Long, type: MediaFile.MediaType): String =
        contentUri(id, type).toString()

    fun contentUri(id: Long, mediaTypeInt: Int): Uri =
        contentUri(id, fromMediaStoreType(mediaTypeInt))

    fun contentUriString(id: Long, mediaTypeInt: Int): String =
        contentUri(id, mediaTypeInt).toString()

    fun collectionUri(type: MediaFile.MediaType): Uri = when (type) {
        MediaFile.MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        MediaFile.MediaType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    fun fromMediaStoreType(mediaTypeInt: Int): MediaFile.MediaType =
        if (mediaTypeInt == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
            MediaFile.MediaType.VIDEO
        } else {
            MediaFile.MediaType.IMAGE
        }
}
