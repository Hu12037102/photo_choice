package com.google.photochoice.config

/**
 * 完成选择时的图片压缩配置（对齐微信朋友圈常见策略：限分辨率 + 限体积）。
 *
 * **策略概要**（由 [com.google.photochoice.util.CompressHelper] 执行）：
 * 1. 等比缩放到不超过 [maxWidth] × [maxHeight]（默认长边 1280）
 * 2. 校正 EXIF 方向后输出 JPEG
 * 3. 若 [maxFileSizeBytes] > 0 且文件仍超限，按 [qualityStep] 递减质量直至达标或 [minQuality]
 *
 * [enabled] 为 `false` 时直接回传 MediaStore 原始 URI。
 *
 * 以下格式即使开启压缩也不会被处理，始终回传原始 URI：
 * - GIF 动图（压缩会丢失动画）
 * - Live / Motion Photo（默认保留动效时）
 *
 * **注意**：压缩输出恒为 JPEG。带透明通道的图片（如透明 PNG/WebP）压缩后
 * 透明区域会变为黑色底（与微信等主流 App 行为一致）；若业务依赖透明度，
 * 请关闭压缩或在宿主侧自行处理。
 */
data class CompressConfig(
    val enabled: Boolean = false,
    /** 输出最大宽度（像素）；0 表示不限制。 */
    val maxWidth: Int = DEFAULT_MAX_EDGE,
    /** 输出最大高度（像素）；0 表示不限制。 */
    val maxHeight: Int = DEFAULT_MAX_EDGE,
    /** JPEG 起始质量（1–100）。 */
    val quality: Int = DEFAULT_QUALITY,
    /**
     * 目标文件体积上限（字节）。
     * 0 表示不启用体积迭代，仅用 [quality] 编码一次。
     */
    val maxFileSizeBytes: Long = DEFAULT_MAX_FILE_SIZE_BYTES,
    /** 体积迭代时的最低 JPEG 质量（1–100）。 */
    val minQuality: Int = DEFAULT_MIN_QUALITY,
    /** 每次体积未达标时递减的质量步长。 */
    val qualityStep: Int = DEFAULT_QUALITY_STEP,
) : java.io.Serializable {
    companion object {
        private const val serialVersionUID = 2L

        /** 默认最长边（像素），对齐朋友圈常见分辨率档位。 */
        const val DEFAULT_MAX_EDGE = 1280

        /** 默认 JPEG 起始质量。 */
        const val DEFAULT_QUALITY = 80

        /** 默认目标体积约 1.5MB。 */
        const val DEFAULT_MAX_FILE_SIZE_BYTES = 1_572_864L

        /** 体积迭代最低质量。 */
        const val DEFAULT_MIN_QUALITY = 50

        /** 质量递减步长。 */
        const val DEFAULT_QUALITY_STEP = 10
    }
}
