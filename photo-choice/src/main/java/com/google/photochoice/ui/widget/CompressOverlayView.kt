package com.google.photochoice.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import com.google.photochoice.R
import com.google.photochoice.util.dp

/**
 * 压缩处理中遮罩：半透明背景 + 居中转圈，拦截交互防止连点。
 *
 * 自封装延迟显示与淡入淡出：调用 [scheduleShow] 后延迟 [SHOW_DELAY_MS] 才淡入，
 * 避免压缩极快完成时遮罩一闪而过；[hide] 淡出并回收。可在预览页 / 网格页 / 裁剪页复用，
 * 让"完成"压缩的可视反馈始终出现在用户当前所在页面。
 *
 * XML 中直接声明即可，无需额外子 View——[ProgressBar] 在 [init] 中动态创建。
 */
class CompressOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** 延迟显示任务，[scheduleShow] 挂起、[hide]/[cancel] 时撤销。 */
    private val showRunnable = Runnable { fadeIn() }

    init {
        // 拦截点击/焦点，压缩期间屏蔽下层交互
        isClickable = true
        isFocusable = true
        setBackgroundColor(OVERLAY_COLOR)
        visibility = View.GONE

        val progress = ProgressBar(context).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.photochoice_preview_text)
            )
            layoutParams = LayoutParams(dp(48), dp(48), Gravity.CENTER)
        }
        addView(progress)
    }

    /**
     * 预约显示：延迟 [SHOW_DELAY_MS] 后淡入。
     * 若压缩在延迟窗口内完成（调用 [hide]），遮罩根本不会出现。
     */
    fun scheduleShow() {
        removeCallbacks(showRunnable)
        postDelayed(showRunnable, SHOW_DELAY_MS)
    }

    /** 隐藏遮罩：撤销未决显示预约并淡出。 */
    fun hide() {
        removeCallbacks(showRunnable)
        if (visibility != View.VISIBLE) {
            visibility = View.GONE
            return
        }
        animate().cancel()
        animate()
            .alpha(0f)
            .setDuration(FADE_OUT_MS)
            .withEndAction { visibility = View.GONE }
            .start()
    }

    private fun fadeIn() {
        animate().cancel()
        alpha = 0f
        visibility = View.VISIBLE
        animate().alpha(1f).setDuration(FADE_IN_MS).start()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(showRunnable)
        animate().cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        /** 遮罩延迟显示阈值：避免快速完成时遮罩一闪而过。 */
        private const val SHOW_DELAY_MS = 200L
        private const val FADE_IN_MS = 150L
        private const val FADE_OUT_MS = 120L
        private const val OVERLAY_COLOR = 0x73000000
    }
}
