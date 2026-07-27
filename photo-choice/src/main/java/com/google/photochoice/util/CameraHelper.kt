package com.google.photochoice.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

/**
 * 相机拍照 Uri 生成器。
 *
 * 通过 MediaStore.Images.Media.insert 写入公共 Pictures/PhotoChoice 目录。
 * API 29+ 采用分区存储的 IS_PENDING 两阶段协议：insert 时置 1 使该行对其它应用不可见，
 * 待相机写完照片后由 [publishImage] 置 0 发布，避免半成品图被相册/其它应用扫描到。
 */
class CameraHelper(private val context: Context) {

    /**
     * 创建拍照目标 Uri。
     * API 29+ 同时写入 IS_PENDING=1，拍照成功后必须调用 [publishImage] 发布，否则照片一直隐藏。
     */
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
            // 分区存储：标记为待写入，写入完成前对媒体库/其它应用不可见
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        return runCatching {
            context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            )
        }.getOrNull()
    }

    /**
     * 拍照成功后发布照片：清除 IS_PENDING 使其在媒体库可见。
     * 仅 API 29+ 需要；低版本无该协议直接可见，空操作。
     */
    fun publishImage(uri: Uri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
        }
        // 更新失败不致命：最坏情况下照片仍处待发布态，不影响主流程不崩溃
        runCatching {
            context.contentResolver.update(uri, values, null, null)
        }
    }
}
