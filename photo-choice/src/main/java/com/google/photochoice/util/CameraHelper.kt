package com.google.photochoice.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

/**
 * 相机拍照 Uri 生成器。
 *
 * 通过 MediaStore.Images.Media.insert 写入公共 Pictures/PhotoChoice 目录，
 * 拍照后系统会自动让该 Uri 可用。
 */
class CameraHelper(private val context: Context) {

    fun createImageUri(): Uri? {
        val values = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "PhotoChoice_${System.currentTimeMillis()}.jpg"
            )
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/PhotoChoice"
            )
        }
        return runCatching {
            context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            )
        }.getOrNull()
    }
}
