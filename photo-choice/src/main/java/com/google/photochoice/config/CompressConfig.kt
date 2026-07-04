package com.google.photochoice.config

/**
 * 完成选择时的图片压缩配置。
 *
 * [enabled] 为 `true` 时，回传前对选中图片执行尺寸 + JPEG 质量压缩（见 [com.google.photochoice.util.CompressHelper]）；
 * 为 `false` 时直接回传 MediaStore 原始 URI。与用户 UI 无关，仅由接入方配置决定。
 *
 * 以下格式即使开启压缩也不会被处理，始终回传原始 URI：
 * - GIF 动图（压缩会丢失动画）
 * - Live / Motion Photo（默认保留动效时）
 */
data class CompressConfig(
    val enabled: Boolean = false,
    val maxWidth: Int = 1920,
    val maxHeight: Int = 1920,
    val quality: Int = 80
)
