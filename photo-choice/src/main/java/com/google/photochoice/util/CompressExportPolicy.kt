package com.google.photochoice.util

import com.google.photochoice.data.model.MediaFile

/**
 * 完成回传时的压缩排除规则。
 *
 * 与 [com.google.photochoice.viewmodel.PhotoChoiceViewModel.shouldCompressOnExport] 配合，
 * 用于判定特定格式在开启压缩时是否仍应保留原始文件。
 */
object CompressExportPolicy {

    private const val MIME_GIF = "image/gif"

    /**
     * 判断媒体是否为 GIF 动图。
     *
     * 优先依据 [MediaFile.mimeType]；MIME 缺失时回退到 [MediaFile.displayName] 扩展名。
     * GIF 经 JPEG 压缩后会丢失动画，故开启压缩时也不应处理。
     */
    fun isGifImage(media: MediaFile): Boolean {
        if (media.type != MediaFile.MediaType.IMAGE) return false
        if (media.mimeType.equals(MIME_GIF, ignoreCase = true)) return true
        return media.displayName.endsWith(".gif", ignoreCase = true)
    }
}
