package com.google.photochoice.util

import com.google.photochoice.config.CompressConfig
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

    /**
     * 判断图片是否低于免压基准（分辨率不超过 720p 基准 **或** 体积小于阈值），
     * 命中任一条即跳过压缩：这类图片再压收益极小甚至负收益（体积不降反升、白损画质）。
     *
     * - 分辨率：长短边分别与基准长短边比较（横竖屏等价）；MediaStore 宽高缺失（0）时
     *   不判定，交由体积条件兜底
     * - 体积：直接用 MediaStore 的 SIZE 列，零 I/O；size 缺失（0）时不判定
     * - 对应阈值配置为 0 表示该维度判定关闭
     */
    fun isBelowCompressBaseline(media: MediaFile, config: CompressConfig): Boolean {
        val longEdgeBase = config.skipCompressBaselineLongEdge
        val shortEdgeBase = config.skipCompressBaselineShortEdge
        if (longEdgeBase > 0 && shortEdgeBase > 0 && media.width > 0 && media.height > 0) {
            val longEdge = maxOf(media.width, media.height)
            val shortEdge = minOf(media.width, media.height)
            if (longEdge <= longEdgeBase && shortEdge <= shortEdgeBase) return true
        }
        val maxBytes = config.skipCompressMaxBytes
        return maxBytes > 0 && media.size in 1 until maxBytes
    }
}
