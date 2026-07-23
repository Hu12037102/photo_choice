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
 * 以下情况即使开启压缩也不会被处理，始终回传原始 URI：
 * - GIF 动图（压缩会丢失动画）
 * - Live / Motion Photo（默认保留动效时；用户在预览页显式选"静态导出"除外）
 * - 低于免压基准的小图（分辨率不超过 720p 基准，或体积小于 [skipCompressMaxBytes]，
 *   见 [com.google.photochoice.util.CompressExportPolicy.isBelowCompressBaseline]）——
 *   这类图片再压收益极小甚至负收益（体积不降反升、白损画质）
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
    /**
     * 免压分辨率基准·长边（像素）：图片长短边分别不超过
     * [skipCompressBaselineLongEdge] × [skipCompressBaselineShortEdge]（横竖屏等价）
     * 时跳过压缩。默认 720p（1280×720）。任一值为 0 表示关闭分辨率免压判定。
     */
    val skipCompressBaselineLongEdge: Int = DEFAULT_SKIP_BASELINE_LONG_EDGE,
    /** 免压分辨率基准·短边（像素），与 [skipCompressBaselineLongEdge] 配套。 */
    val skipCompressBaselineShortEdge: Int = DEFAULT_SKIP_BASELINE_SHORT_EDGE,
    /** 免压体积阈值（字节）：文件小于该值跳过压缩。默认 150KB；0 表示关闭体积免压判定。 */
    val skipCompressMaxBytes: Long = DEFAULT_SKIP_MAX_BYTES,
) : java.io.Serializable {
    companion object {
        private const val serialVersionUID = 3L

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

        /** 免压基准长边：720p 的 1280。 */
        const val DEFAULT_SKIP_BASELINE_LONG_EDGE = 1280

        /** 免压基准短边：720p 的 720。 */
        const val DEFAULT_SKIP_BASELINE_SHORT_EDGE = 720

        /** 免压体积阈值 150KB：该量级的 JPEG 本已高度压缩，再压体积可能不降反升。 */
        const val DEFAULT_SKIP_MAX_BYTES = 150L * 1024
    }
}
