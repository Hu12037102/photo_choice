package com.google.photochoice.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * 文字精确视觉居中的 TextView。
 *
 * 标准 TextView 的 gravity=center 按 fontMetrics 的 ascent/descent 居中"文字行"，
 * 且 includeFontPadding 默认 true 会在字形上方引入额外 padding——两者叠加导致
 * 数字等字形在圆形/方形角标内视觉偏移（通常偏下），圆形背景对这种偏移尤其敏感。
 *
 * 本类重写 [onDraw]，用 [android.graphics.Paint.getTextBounds] 取实际字形 bounds，
 * 按 bounds 几何中心对齐容器中心绘制，实现真正的视觉居中。
 * 适用于选中序号角标等对居中敏感的纯文字场景。
 *
 * 注意：重写后 gravity / includeFontPadding 属性不再影响绘制（始终视觉居中）；
 * compoundDrawables、文字阴影等不渲染，故仅适合纯文字角标。
 */
class CenteredTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val textBounds = Rect()

    override fun onDraw(canvas: Canvas) {
        val text = text?.toString().orEmpty()
        if (text.isEmpty()) return
        val paint = paint
        paint.getTextBounds(text, 0, text.length, textBounds)
        // bounds.top/bottom 相对 baseline（top 为负，bottom 为正）。
        // 字形几何中心相对 baseline = (top + bottom) / 2；
        // 令该中心对齐容器垂直中心，得 baseline 坐标：
        val baseline = height / 2f - (textBounds.top + textBounds.bottom) / 2f
        // 水平方向同理，按 bounds 几何中心对齐容器水平中心：
        val x = (width - textBounds.width()) / 2f - textBounds.left
        canvas.drawText(text, x, baseline, paint)
    }
}
