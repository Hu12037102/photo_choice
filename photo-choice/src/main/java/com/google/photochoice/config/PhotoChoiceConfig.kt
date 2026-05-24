package com.google.photochoice.config

/**
 * PhotoChoice 全量配置。所有字段均为不可变；通过 Builder 或 copy() 创建。
 *
 * 选择数量语义：
 * - [selectCount] == 1：单选
 * - [selectCount] > 1：多选
 * 合法范围 1..9，超出区间会被自动 clamp 到 1。
 */
data class PhotoChoiceConfig(
    val selectCount: Int = 9,
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
    companion object {
        const val SELECT_COUNT_MIN = 1
        const val SELECT_COUNT_MAX = 9
    }

    /** 安全的 selectCount：超出 [SELECT_COUNT_MIN]..[SELECT_COUNT_MAX] 区间时回退到 1。 */
    val sanitizedSelectCount: Int =
        if (selectCount in SELECT_COUNT_MIN..SELECT_COUNT_MAX) selectCount else SELECT_COUNT_MIN

    val isSingleSelect: Boolean get() = sanitizedSelectCount == 1

    init {
        require(spanCount in 2..6) { "spanCount must be in [2, 6]" }
    }
}
