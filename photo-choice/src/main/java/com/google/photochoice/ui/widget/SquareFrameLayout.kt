package com.google.photochoice.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * 始终保持 1:1 宽高比的 FrameLayout。
 * 网格缩略图的根布局使用此 View，按测量得到的 width 强制 height 与之相等。
 */
class SquareFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}
