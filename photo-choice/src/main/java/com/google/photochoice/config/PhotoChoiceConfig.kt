package com.google.photochoice.config

/**
 * PhotoChoice 全量配置。所有字段均为不可变；通过 Builder 或 copy() 创建。
 */
data class PhotoChoiceConfig(
    val maxSelectCount: Int = 9,
    val minSelectCount: Int = 1,
    val selectMode: SelectMode = SelectMode.MULTI,
    val mediaType: MediaType = MediaType.IMAGE,
    val spanCount: Int = 4,
    val showCamera: Boolean = true,
    val minImageSize: Long = 0L,
    val maxImageSize: Long = Long.MAX_VALUE,
    val minVideoDurationMs: Long = 0L,
    val maxVideoDurationMs: Long = 60_000L,
    val themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    val cropConfig: CropConfig = CropConfig(),
    val compressConfig: CompressConfig = CompressConfig()
) {
    init {
        require(maxSelectCount >= 1) { "maxSelectCount must be >= 1" }
        require(minSelectCount in 1..maxSelectCount) {
            "minSelectCount must be in [1, maxSelectCount]"
        }
        require(spanCount in 2..6) { "spanCount must be in [2, 6]" }
    }
}
