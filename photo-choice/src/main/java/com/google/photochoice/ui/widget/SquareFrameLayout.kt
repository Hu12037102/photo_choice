package com.google.photochoice.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View.MeasureSpec
import android.widget.FrameLayout

/**
 * 始终保持 1:1 宽高比的 FrameLayout。网格缩略图根布局使用，按列宽强制为正方形。
 */
class SquareFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val squareSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        super.onMeasure(squareSpec, squareSpec)
    }
}
