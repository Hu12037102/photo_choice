package com.google.photochoice.ui.widget

import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.google.photochoice.R
import com.google.photochoice.config.DesignTokens
import com.google.photochoice.util.dp

/**
 * Toolbar 下划线处的滑动日期条。
 *
 * 置于 [R.id.dateHeaderClipHost] 内，自分割线向下滑出 / 向上收回（纯 translationY + 父级 clip）。
 */
class ScrollingDateHeaderBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val tvDate: AppCompatTextView
    private var hiddenTranslationY = 0f
    private var shown = false
    private var currentLabel: String? = null
    private var labelFadeRunning = false

    init {
        LayoutInflater.from(context).inflate(R.layout.view_scrolling_date_header, this, true)
        tvDate = findViewById(R.id.tvScrollingDate)
        hiddenTranslationY = -context.dp(DesignTokens.DATE_HEADER_HEIGHT_DP).toFloat()
        visibility = INVISIBLE
        translationY = hiddenTranslationY
        isClickable = false
        isFocusable = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h <= 0) return
        hiddenTranslationY = -h.toFloat()
        if (!shown) {
            translationY = hiddenTranslationY
        }
    }

    fun setDateLabel(label: String, crossfade: Boolean = true) {
        if (label == currentLabel) return
        val previous = currentLabel
        currentLabel = label
        if (!crossfade || previous == null || !shown) {
            labelFadeRunning = false
            tvDate.animate().cancel()
            tvDate.alpha = 1f
            tvDate.text = label
            return
        }
        if (labelFadeRunning) {
            tvDate.text = label
            return
        }
        labelFadeRunning = true
        tvDate.animate().cancel()
        tvDate.animate()
            .alpha(0f)
            .setDuration(DesignTokens.DATE_HEADER_LABEL_FADE_OUT_MS)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                tvDate.text = label
                tvDate.animate()
                    .alpha(1f)
                    .setDuration(DesignTokens.DATE_HEADER_LABEL_FADE_IN_MS)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction { labelFadeRunning = false }
                    .start()
            }
            .start()
    }

    fun show() {
        if (shown) return
        shown = true
        animate().cancel()
        tvDate.animate().cancel()
        bringToFront()
        (parent as? FrameLayout)?.bringToFront()
        visibility = VISIBLE
        translationY = hiddenTranslationY
        animate()
            .translationY(0f)
            .setDuration(DesignTokens.DATE_HEADER_SHOW_MS)
            .setInterpolator(FastOutSlowInInterpolator())
            .setListener(null)
            .start()
    }

    fun hide() {
        if (!shown) return
        shown = false
        animate().cancel()
        animate()
            .translationY(hiddenTranslationY)
            .setDuration(DesignTokens.DATE_HEADER_HIDE_MS)
            .setInterpolator(FastOutLinearInInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!shown) {
                        visibility = INVISIBLE
                    }
                }
            })
            .start()
    }

    fun hideImmediately() {
        shown = false
        labelFadeRunning = false
        tvDate.animate().cancel()
        animate().cancel()
        visibility = INVISIBLE
        translationY = hiddenTranslationY
        tvDate.alpha = 1f
    }

    override fun onDetachedFromWindow() {
        animate().cancel()
        tvDate.animate().cancel()
        super.onDetachedFromWindow()
    }
}
