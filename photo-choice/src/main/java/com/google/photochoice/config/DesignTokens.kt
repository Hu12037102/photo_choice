package com.google.photochoice.config

/** 设计常量：仅保留代码中实际引用的项。 */
object DesignTokens {

    const val GRID_SPACING_DP = 2

    const val ALBUM_DROPDOWN_MAX_FRACTION = 0.75f
    const val ALBUM_DROPDOWN_ANIM_SHOW_MS = 250L
    const val ALBUM_DROPDOWN_ANIM_DISMISS_MS = 200L
    const val TOOLBAR_CHEVRON_ANIM_MS = 250L

    const val BOTTOM_BAR_ANIM_SHOW_MS = 280L
    const val BOTTOM_BAR_ANIM_DISMISS_MS = 240L

    /** 日期悬浮胶囊显隐动画时长（右滑出入）；隐藏位移改由 View 运行期按实测几何计算，不再需要高度常量。 */
    const val DATE_HEADER_SHOW_MS = 280L
    const val DATE_HEADER_HIDE_MS = 220L
    const val DATE_HEADER_IDLE_HOLD_MS = 800L
    const val DATE_HEADER_LABEL_FADE_OUT_MS = 80L
    const val DATE_HEADER_LABEL_FADE_IN_MS = 120L
}
