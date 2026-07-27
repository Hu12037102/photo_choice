package com.google.photochoice.ui.widget

import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.google.photochoice.R
import com.google.photochoice.config.DesignTokens

/**
 * Toolbar 下划线处的滑动日期条。
 *
 * 置于 [R.id.dateHeaderClipHost] 内，右对齐胶囊 [R.id.dateChip] 自右边界滑入 / 向右收回
 * （纯 translationX + 父级 clip）。仅对胶囊本身做平移，根布局透明、不参与动画。
 */
class ScrollingDateHeaderBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    /** 实际参与显隐动画的右对齐胶囊；根布局透明只作定位与裁剪宿主。 */
    private val dateChip: View
    private val tvDate: AppCompatTextView

    /** 胶囊隐藏态的 X 位移：向右退出到 clip 容器边界之外（正值向右）。onSizeChanged 后按实测宽度校正。 */
    private var hiddenTranslationX = 0f
    private var shown = false
    private var currentLabel: String? = null
    private var labelFadeRunning = false

    init {
        LayoutInflater.from(context).inflate(R.layout.view_scrolling_date_header, this, true)
        dateChip = findViewById(R.id.dateChip)
        tvDate = findViewById(R.id.tvScrollingDate)
        visibility = INVISIBLE
        isClickable = false
        isFocusable = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        refreshHiddenTranslationX()
        if (!shown) {
            dateChip.translationX = hiddenTranslationX
        }
    }

    /**
     * 按胶囊当前布局位置校正隐藏态 X 位移。
     * 隐藏位移 = 本容器宽 - 胶囊左边界（等价于胶囊宽 + 右外边距），使胶囊完全退出右边界外被父级裁剪。
     * onSizeChanged 早于子 View 布局（setFrame 先于 onLayout），此时 dateChip.left 可能为 0，
     * 故 show()/hideImmediately() 使用前会再次调用本方法按实测几何校正，避免首帧位移偏大。
     */
    private fun refreshHiddenTranslationX() {
        val w = width
        if (w <= 0) return
        hiddenTranslationX = (w - dateChip.left).toFloat()
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
        // 已有淡入淡出在跑：交给运行中的动画在结束时取 currentLabel 最新值，避免闭包捕获旧值导致错位。
        if (labelFadeRunning) return
        startLabelCrossfade()
    }

    /**
     * 始终在淡出末尾从 [currentLabel] 读取最新文案，避免快速滚动时因闭包捕获旧 label 造成日期与缩略图错位。
     * 若淡入期间又收到新值，结束后接力再做一次淡出淡入，对齐到最终值。
     */
    private fun startLabelCrossfade() {
        labelFadeRunning = true
        tvDate.animate().cancel()
        tvDate.animate()
            .alpha(0f)
            .setDuration(DesignTokens.DATE_HEADER_LABEL_FADE_OUT_MS)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                val next = currentLabel
                if (next == null) {
                    labelFadeRunning = false
                    tvDate.alpha = 1f
                    return@withEndAction
                }
                tvDate.text = next
                tvDate.animate()
                    .alpha(1f)
                    .setDuration(DesignTokens.DATE_HEADER_LABEL_FADE_IN_MS)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction {
                        labelFadeRunning = false
                        val latest = currentLabel
                        if (latest != null && latest != tvDate.text.toString()) {
                            startLabelCrossfade()
                        }
                    }
                    .start()
            }
            .start()
    }

    /** 从右边界外滑入胶囊：先把根布局提到最前保证不被网格遮挡，再对胶囊做 translationX 归零动画。 */
    fun show() {
        if (shown) return
        shown = true
        dateChip.animate().cancel()
        tvDate.animate().cancel()
        bringToFront()
        (parent as? FrameLayout)?.bringToFront()
        // 布局已完成，按胶囊实际位置校正隐藏位移，规避首帧 onSizeChanged 早于子 View 布局的偏差。
        refreshHiddenTranslationX()
        visibility = VISIBLE
        dateChip.translationX = hiddenTranslationX
        dateChip.animate()
            .translationX(0f)
            .setDuration(DesignTokens.DATE_HEADER_SHOW_MS)
            .setInterpolator(FastOutSlowInInterpolator())
            .setListener(null)
            .start()
    }

    /** 向右收回胶囊：动画结束后（仍处隐藏态时）把根布局置为 INVISIBLE，释放绘制。 */
    fun hide() {
        if (!shown) return
        shown = false
        dateChip.animate().cancel()
        dateChip.animate()
            .translationX(hiddenTranslationX)
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

    /** 无动画立即隐藏：取消动画、根布局 INVISIBLE，并把胶囊复位到右侧隐藏位。 */
    fun hideImmediately() {
        shown = false
        labelFadeRunning = false
        tvDate.animate().cancel()
        dateChip.animate().cancel()
        visibility = INVISIBLE
        refreshHiddenTranslationX()
        dateChip.translationX = hiddenTranslationX
        tvDate.alpha = 1f
    }

    override fun onDetachedFromWindow() {
        dateChip.animate().cancel()
        tvDate.animate().cancel()
        super.onDetachedFromWindow()
    }
}
