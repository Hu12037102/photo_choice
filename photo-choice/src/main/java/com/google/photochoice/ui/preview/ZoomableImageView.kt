package com.google.photochoice.ui.preview

import android.animation.AnimatorListenerAdapter
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
 * - 不支持单指拖拽平移
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

    /** 单击（非双击）回调。 */
    var onSingleTapListener: (() -> Unit)? = null

    /** 长按回调（用于实况图播放等）。 */
    var onLongPressListener: (() -> Unit)? = null

    /**
     * 长按抬手回调：仅在 [onLongPressListener] 已触发后的对应抬手时调用，
     * 不会随双指缩放等其它手势的 ACTION_UP 误触发。
     */
    var onLongPressReleaseListener: (() -> Unit)? = null

    /** 长按播放进行中时屏蔽单击 / 双击，避免误触全屏。 */
    var isLongPressInteractionActive: Boolean = false

    /** 已触发长按、等待抬手结束实况播放。 */
    private var awaitingLongPressRelease = false

    /** 缩放倍数变化（手势结束或双击动画结束后）。 */
    var onZoomStateChanged: ((Boolean) -> Unit)? = null

    /** 双指 pinch 进行中。 */
    var onScalingChanged: ((Boolean) -> Unit)? = null

    private var lastNotifiedZoomed = false
    private var isPinching = false
    private var activePointerCount = 0
    /** 本次触摸序列是否出现过多指（pinch 等），用于避免抬手时误触单击全屏。 */
    private var multiTouchInCurrentSequence = false

    init {
        scaleType = ScaleType.MATRIX
    }

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isPinching = true
                multiTouchInCurrentSequence = true
                requestDisallowParentIntercept(true)
                onScalingChanged?.invoke(true)
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val cur = currentScale()
                val target = (cur * detector.scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE)
                val factor = target / cur
                if (factor == 1f) return true
                drawMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
                imageMatrix = drawMatrix
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isPinching = false
                onScalingChanged?.invoke(false)
                fixTranslation()
                imageMatrix = drawMatrix
                notifyZoomState()
                if (!isZoomed) {
                    requestDisallowParentIntercept(false)
                }
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isLongPressInteractionActive) return false
                onSingleTapListener?.invoke()
                return onSingleTapListener != null
            }

            override fun onLongPress(e: MotionEvent) {
                if (isLongPressInteractionActive) return
                if (activePointerCount != 1) return
                if (multiTouchInCurrentSequence || scaleDetector.isInProgress || isPinching) return
                val listener = onLongPressListener ?: return
                awaitingLongPressRelease = true
                listener.invoke()
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isLongPressInteractionActive) return true
                val cur = currentScale()
                val target = if (cur > MIN_SCALE * 1.5f) MIN_SCALE else DOUBLE_TAP_SCALE
                animateToScale(target, e.x, e.y)
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
        requestDisallowParentIntercept(true)
        onScalingChanged?.invoke(true)
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIMATE_DURATION
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                val fraction = va.animatedFraction
                val cur = currentScale()
                val current = startScale + (target - startScale) * fraction
                val factor = current / cur
                if (factor != 1f) {
                    drawMatrix.postScale(factor, factor, pivotX, pivotY)
                    imageMatrix = drawMatrix
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onScalingChanged?.invoke(false)
                    fixTranslation()
                    imageMatrix = drawMatrix
                    notifyZoomState()
                    if (!isZoomed) {
                        requestDisallowParentIntercept(false)
                    }
                }
            })
            start()
        }
    }

    private fun notifyZoomState() {
        val zoomed = isZoomed
        if (zoomed != lastNotifiedZoomed) {
            lastNotifiedZoomed = zoomed
            onZoomStateChanged?.invoke(zoomed)
        }
    }

    private fun requestDisallowParentIntercept(disallow: Boolean) {
        var p = parent
        while (p != null) {
            p.requestDisallowInterceptTouchEvent(disallow)
            p = p.parent
        }
    }

    private fun completeLongPressReleaseIfNeeded(event: MotionEvent) {
        if (!awaitingLongPressRelease) return
        val isLastPointerUp = event.actionMasked == MotionEvent.ACTION_UP && event.pointerCount == 1
        val isCancel = event.actionMasked == MotionEvent.ACTION_CANCEL
        if (!isCancel && (scaleDetector.isInProgress || isPinching)) return
        if (!isLastPointerUp && !isCancel) return
        awaitingLongPressRelease = false
        onLongPressReleaseListener?.invoke()
    }

    private fun cancelLongPressReleaseForMultiTouch() {
        if (!awaitingLongPressRelease) return
        awaitingLongPressRelease = false
        onLongPressReleaseListener?.invoke()
    }

    private fun cancelGestureDetection(event: MotionEvent) {
        val cancelEvent = MotionEvent.obtain(event)
        cancelEvent.action = MotionEvent.ACTION_CANCEL
        gestureDetector.onTouchEvent(cancelEvent)
        cancelEvent.recycle()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerCount = 1
                multiTouchInCurrentSequence = false
                if (isZoomed) {
                    requestDisallowParentIntercept(true)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                activePointerCount = event.pointerCount
                multiTouchInCurrentSequence = true
                requestDisallowParentIntercept(true)
                cancelGestureDetection(event)
                cancelLongPressReleaseForMultiTouch()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                activePointerCount = (event.pointerCount - 1).coerceAtLeast(0)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerCount = event.pointerCount
            }
        }

        scaleDetector.onTouchEvent(event)

        val allowSingleFingerTap = event.pointerCount <= 1 &&
            !scaleDetector.isInProgress &&
            !isPinching &&
            !multiTouchInCurrentSequence
        if (allowSingleFingerTap) {
            gestureDetector.onTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                completeLongPressReleaseIfNeeded(event)
                if (!scaleDetector.isInProgress && !isPinching) {
                    if (!isZoomed) {
                        requestDisallowParentIntercept(false)
                    }
                    notifyZoomState()
                }
            }
        }

        if (event.actionMasked == MotionEvent.ACTION_UP && event.pointerCount == 1) {
            activePointerCount = 0
            multiTouchInCurrentSequence = false
        } else if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            activePointerCount = 0
            multiTouchInCurrentSequence = false
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
