package com.google.photochoice.ui.preview

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatImageView

/**
 * 大图预览缩放视图。
 *
 * - 初始：fit-center 显示图片
 * - 双指 pinch：1x ~ 3x
 * - 双击：1x ↔ 2x 切换
 * - 单指拖拽：仅在 scale > 1 时生效（图片平移）
 *
 * 通过 ImageMatrix 实现，不修改 scaleType（外部不能再设 scaleType）。
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    companion object {
        private const val MIN_SCALE = 1.0f
        private const val MAX_SCALE = 3.0f
        private const val DOUBLE_TAP_SCALE = 2.0f
        private const val ANIMATE_DURATION = 220L
    }

    private val baseMatrix = Matrix()
    private val drawMatrix = Matrix()
    private val tempMatrix = Matrix()
    private val tempValues = FloatArray(9)
    private val displayRect = RectF()

    val isZoomed: Boolean
        get() = currentScale() > MIN_SCALE * 1.01f

    init {
        scaleType = ScaleType.MATRIX
    }

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val cur = currentScale()
                val target = (cur * detector.scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE)
                val factor = target / cur
                drawMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
                fixTranslation()
                imageMatrix = drawMatrix
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val cur = currentScale()
                val target = if (cur > MIN_SCALE * 1.5f) MIN_SCALE else DOUBLE_TAP_SCALE
                animateToScale(target, e.x, e.y)
                return true
            }

            override fun onScroll(
                e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float
            ): Boolean {
                if (currentScale() <= MIN_SCALE * 1.01f) return false
                drawMatrix.postTranslate(-dx, -dy)
                fixTranslation()
                imageMatrix = drawMatrix
                return true
            }
        }
    )

    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        super.setImageDrawable(drawable)
        applyBaseMatrix()
    }

    override fun setImageURI(uri: android.net.Uri?) {
        super.setImageURI(uri)
        applyBaseMatrix()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyBaseMatrix()
    }

    fun resetScale() {
        animateToScale(MIN_SCALE, width / 2f, height / 2f)
    }

    private fun applyBaseMatrix() {
        val d = drawable ?: return
        if (width <= 0 || height <= 0) return
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        if (dw <= 0f || dh <= 0f) return

        val viewRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val drawableRect = RectF(0f, 0f, dw, dh)
        baseMatrix.reset()
        baseMatrix.setRectToRect(drawableRect, viewRect, Matrix.ScaleToFit.CENTER)
        drawMatrix.set(baseMatrix)
        imageMatrix = drawMatrix
    }

    private fun currentScale(): Float {
        drawMatrix.getValues(tempValues)
        val scaleX = tempValues[Matrix.MSCALE_X]
        val skewY = tempValues[Matrix.MSKEW_Y]
        val total = kotlin.math.sqrt(scaleX * scaleX + skewY * skewY)
        baseMatrix.getValues(tempValues)
        val baseScale = kotlin.math.sqrt(
            tempValues[Matrix.MSCALE_X] * tempValues[Matrix.MSCALE_X] +
                tempValues[Matrix.MSKEW_Y] * tempValues[Matrix.MSKEW_Y]
        )
        return if (baseScale <= 0f) MIN_SCALE else total / baseScale
    }

    private fun fixTranslation() {
        val rect = currentDisplayRect() ?: return
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        var dx = 0f
        var dy = 0f

        if (rect.width() <= viewW) {
            dx = (viewW - rect.width()) / 2f - rect.left
        } else {
            if (rect.left > 0) dx = -rect.left
            else if (rect.right < viewW) dx = viewW - rect.right
        }

        if (rect.height() <= viewH) {
            dy = (viewH - rect.height()) / 2f - rect.top
        } else {
            if (rect.top > 0) dy = -rect.top
            else if (rect.bottom < viewH) dy = viewH - rect.bottom
        }

        drawMatrix.postTranslate(dx, dy)
    }

    private fun currentDisplayRect(): RectF? {
        val d = drawable ?: return null
        displayRect.set(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        drawMatrix.mapRect(displayRect)
        return displayRect
    }

    private fun animateToScale(target: Float, pivotX: Float, pivotY: Float) {
        val startScale = currentScale()
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIMATE_DURATION
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                val fraction = va.animatedFraction
                val cur = currentScale()
                val current = startScale + (target - startScale) * fraction
                val factor = current / cur
                drawMatrix.postScale(factor, factor, pivotX, pivotY)
                fixTranslation()
                imageMatrix = drawMatrix
            }
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
