package com.google.photochoice.ui.preview

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.util.AttributeSet
import android.view.View
import android.view.animation.PathInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.withScale
import com.google.photochoice.R
import kotlin.math.min

/**
 * 实况导出开关勾选图标：空心圆环 ↔ 实心圆 + 勾路径动画。
 */
class LiveExportCheckIcon @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val checkSource = Path()
    private val checkDraw = Path()
    private val pathMeasure = PathMeasure()

    private var fillProgress = 1f
    private var checkProgress = 1f
    private var ringAlpha = 0f
    private var checked = true
    private var runningAnimator: Animator? = null

    private val strokeWidthPx = resources.displayMetrics.density * 1.5f
    private val checkStrokePx = resources.displayMetrics.density * 1.75f
    private val colorWhite = ContextCompat.getColor(context, R.color.photochoice_preview_text)
    private val colorCheckOnFill = ContextCompat.getColor(context, R.color.photochoice_preview_btn_text_enabled)

    /** 未选中圆环相对不透明度，与文案弱化一致 */
    private val uncheckedRingAlpha = 0.58f

    /** 勾选路径插值：略带回弹，路径感更明显 */
    private val checkPathInterpolator = PathInterpolator(0.2f, 0f, 0.2f, 1f)

    init {
        strokePaint.strokeWidth = strokeWidthPx
        checkPaint.strokeWidth = checkStrokePx
        applyAppearance()
        applyInstant(checked = true)
    }

    /**
     * 初始化各画笔颜色：实心白圆、深色勾、白色圆环。
     * 颜色为固定主题常量、不随勾选态变化，故仅需在构造时应用一次。
     */
    private fun applyAppearance() {
        fillPaint.color = colorWhite
        checkPaint.color = colorCheckOnFill
        strokePaint.color = colorWhite
    }

    /**
     * 更新勾选态；[animate] 为 true 时播放填充与勾画/回退动画。
     */
    fun setChecked(checked: Boolean, animate: Boolean) {
        if (this.checked == checked && runningAnimator == null && isVisuallyAt(checked)) return
        this.checked = checked
        runningAnimator?.cancel()
        if (!animate) {
            applyInstant(checked)
            invalidate()
            return
        }
        runningAnimator = if (checked) buildCheckAnimator() else buildUncheckAnimator()
        runningAnimator?.start()
    }

    private fun isVisuallyAt(target: Boolean): Boolean =
        if (target) {
            fillProgress >= 0.99f && checkProgress >= 0.99f && ringAlpha <= 0.01f
        } else {
            fillProgress <= 0.01f && checkProgress <= 0.01f && ringAlpha >= 0.99f
        }

    private fun applyInstant(checked: Boolean) {
        if (checked) {
            fillProgress = 1f
            checkProgress = 1f
            ringAlpha = 0f
        } else {
            fillProgress = 0f
            checkProgress = 0f
            ringAlpha = 1f
        }
    }

    /** 勾选：填充与勾路径同步，勾路径 300ms */
    private fun buildCheckAnimator(): AnimatorSet {
        val fillAnim = ValueAnimator.ofFloat(fillProgress, 1f).apply {
            duration = FILL_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                fillProgress = it.animatedValue as Float
                ringAlpha = 1f - fillProgress
                invalidate()
            }
        }
        val checkAnim = ValueAnimator.ofFloat(checkProgress, 1f).apply {
            duration = CHECK_PATH_DURATION_MS
            interpolator = checkPathInterpolator
            addUpdateListener {
                checkProgress = it.animatedValue as Float
                invalidate()
            }
        }
        return AnimatorSet().apply {
            playTogether(fillAnim, checkAnim)
            addListener(onEndListener())
        }
    }

    /** 取消勾选：勾路径先回退 300ms，再收缩填充 */
    private fun buildUncheckAnimator(): AnimatorSet {
        val checkAnim = ValueAnimator.ofFloat(checkProgress, 0f).apply {
            duration = CHECK_PATH_DURATION_MS
            interpolator = checkPathInterpolator
            addUpdateListener {
                checkProgress = it.animatedValue as Float
                invalidate()
            }
        }
        val fillAnim = ValueAnimator.ofFloat(fillProgress, 0f).apply {
            duration = FILL_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                fillProgress = it.animatedValue as Float
                ringAlpha = 1f - fillProgress
                invalidate()
            }
        }
        return AnimatorSet().apply {
            play(checkAnim).before(fillAnim)
            addListener(onEndListener())
        }
    }

    private fun onEndListener() = object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            runningAnimator = null
            applyInstant(checked)
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy) - strokeWidthPx

        if (fillProgress > 0f) {
            canvas.withScale(fillProgress, fillProgress, cx, cy) {
                fillPaint.alpha = (fillProgress * 255).toInt().coerceIn(0, 255)
                drawCircle(cx, cy, radius, fillPaint)
            }
        }

        if (ringAlpha > 0f) {
            val baseAlpha = if (checked) 1f else uncheckedRingAlpha
            strokePaint.alpha = (ringAlpha * baseAlpha * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, radius, strokePaint)
        }

        if (checkProgress > 0f) {
            buildCheckPath(cx, cy, radius)
            pathMeasure.setPath(checkSource, false)
            val length = pathMeasure.length
            checkDraw.reset()
            pathMeasure.getSegment(0f, length * checkProgress, checkDraw, true)
            checkPaint.alpha = 255
            canvas.drawPath(checkDraw, checkPaint)
        }
    }

    private fun buildCheckPath(cx: Float, cy: Float, radius: Float) {
        val left = cx - radius * 0.42f
        val mid = cy + radius * 0.02f
        val right = cx + radius * 0.48f
        val top = cy - radius * 0.34f
        checkSource.reset()
        checkSource.moveTo(left, mid)
        checkSource.lineTo(cx - radius * 0.02f, cy + radius * 0.38f)
        checkSource.lineTo(right, top)
    }

    override fun onDetachedFromWindow() {
        runningAnimator?.cancel()
        runningAnimator = null
        super.onDetachedFromWindow()
    }

    companion object {
        private const val FILL_DURATION_MS = 200L
        private const val CHECK_PATH_DURATION_MS = 300L
    }
}
