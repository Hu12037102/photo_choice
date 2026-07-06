package com.google.photochoice.ui.preview

import android.animation.Animator
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
import kotlin.math.abs

/**
 * 大图预览缩放视图。
 *
 * - 初始：fit-center 显示图片
 * - 双指 pinch：1x ~ 3x，越界有阻尼回弹
 * - 双击：1x ↔ 2x 切换
 * - 放大后单指拖拽平移图片，越界有橡皮筋阻尼，松手回弹
 * - 拖到图片水平边界后继续拖拽则交由 ViewPager2 切页
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
        /** 双击缩放动画时长。 */
        private const val ANIMATE_DURATION = 220L
        /** 越界回弹动画时长。 */
        private const val BOUND_ANIM_DURATION = 280L
        /**
         * 越界阻尼系数（0~1）：越小阻尼越强。
         * 拖拽越界时手指每移动 100px，图片实际只移动 25px。
         */
        private const val OVERSCROLL_DAMPING = 0.25f
    }

    private val baseMatrix = Matrix()
    private val drawMatrix = Matrix()
    private val tempMatrix = Matrix()
    private val tempValues = FloatArray(9)
    private val displayRect = RectF()

    /** 单指拖拽：记录上一次触摸位置，用于计算 delta。 */
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    /** 当前触摸序列中是否正在进行单指拖拽平移。 */
    private var isDragging = false
    /** 越界回弹动画。 */
    private var boundAnimator: ValueAnimator? = null

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
                cancelBoundAnimation()
                requestDisallowParentIntercept(true)
                onScalingChanged?.invoke(true)
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val cur = currentScale()
                val rawTarget = cur * detector.scaleFactor

                // pinch 越界阻尼：允许略微超出 MIN/MAX，但施加橡皮筋阻力
                val damped = if (rawTarget < MIN_SCALE) {
                    val overshoot = MIN_SCALE - rawTarget
                    MIN_SCALE - overshoot * OVERSCROLL_DAMPING
                } else if (rawTarget > MAX_SCALE) {
                    val overshoot = rawTarget - MAX_SCALE
                    MAX_SCALE + overshoot * OVERSCROLL_DAMPING
                } else {
                    rawTarget
                }
                // 硬上限兜底，防止极端情况越界太远
                val target = damped.coerceIn(MIN_SCALE * 0.7f, MAX_SCALE * 1.2f)
                val factor = target / cur
                if (factor == 1f) return true
                drawMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
                imageMatrix = drawMatrix
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isPinching = false
                onScalingChanged?.invoke(false)
                // 动画回弹到合法边界（缩放 + 位移一起修正）
                snapToBounds(animated = true)
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
                if (isDragging) return
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

    // ── 边界修正与动画 ────────────────────────────────────────────────────

    /**
     * 修正图片缩放与位移到合法边界。
     *
     * @param animated true 时使用 DecelerateInterpolator 动画过渡，false 时立即修正。
     */
    private fun snapToBounds(animated: Boolean) {
        cancelBoundAnimation()

        // 1. 缩放修正：回到 [MIN_SCALE, MAX_SCALE] 范围内
        val cur = currentScale()
        val targetScale = cur.coerceIn(MIN_SCALE, MAX_SCALE)
        val scaleCorrection = if (cur > 0f) targetScale / cur else 1f

        // 2. 位移修正：计算需要修正的 dx/dy
        val rect = currentDisplayRect() ?: return
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        var correctionDx = 0f
        var correctionDy = 0f

        if (rect.width() <= viewW) {
            correctionDx = (viewW - rect.width()) / 2f - rect.left
        } else {
            if (rect.left > 0) correctionDx = -rect.left
            else if (rect.right < viewW) correctionDx = viewW - rect.right
        }

        if (rect.height() <= viewH) {
            correctionDy = (viewH - rect.height()) / 2f - rect.top
        } else {
            if (rect.top > 0) correctionDy = -rect.top
            else if (rect.bottom < viewH) correctionDy = viewH - rect.bottom
        }

        // 调整位移修正量以补偿缩放修正带来的位移变化
        if (scaleCorrection != 1f) {
            correctionDx = correctionDx * scaleCorrection
            correctionDy = correctionDy * scaleCorrection
        }

        if (scaleCorrection == 1f && correctionDx == 0f && correctionDy == 0f) return

        if (!animated) {
            // 立即修正
            if (scaleCorrection != 1f) {
                drawMatrix.postScale(scaleCorrection, scaleCorrection, viewW / 2f, viewH / 2f)
            }
            if (correctionDx != 0f || correctionDy != 0f) {
                drawMatrix.postTranslate(correctionDx, correctionDy)
            }
            imageMatrix = drawMatrix
            return
        }

        // 动画修正：从 0 平滑过渡到目标修正量
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = BOUND_ANIM_DURATION
            interpolator = DecelerateInterpolator()
            var lastFraction = 0f
            addUpdateListener { va ->
                val fraction = va.animatedFraction
                val step = fraction - lastFraction
                if (step > 0f) {
                    if (scaleCorrection != 1f) {
                        val stepScale = 1f + (scaleCorrection - 1f) * step / (1f - lastFraction)
                        drawMatrix.postScale(stepScale, stepScale, viewW / 2f, viewH / 2f)
                    }
                    drawMatrix.postTranslate(correctionDx * step, correctionDy * step)
                    imageMatrix = drawMatrix
                }
                lastFraction = fraction
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // 确保精确到达目标位置
                    val remainingFraction = 1f - lastFraction
                    if (remainingFraction > 0f) {
                        if (scaleCorrection != 1f) {
                            val remainingScale = 1f + (scaleCorrection - 1f) * remainingFraction
                            drawMatrix.postScale(remainingScale, remainingScale, viewW / 2f, viewH / 2f)
                        }
                        drawMatrix.postTranslate(
                            correctionDx * remainingFraction,
                            correctionDy * remainingFraction
                        )
                        imageMatrix = drawMatrix
                    }
                    boundAnimator = null
                }
            })
        }
        boundAnimator = anim
        anim.start()
    }

    /**
     * 对当前矩阵施加越界拖拽阻尼。
     *
     * 先让图片完整跟随手指，再根据越界量反向拉回一部分，
     * 产生"橡皮筋"手感——越界越多阻力越大，但不会完全锁死。
     */
    private fun applyOverscrollResistance() {
        val rect = currentDisplayRect() ?: return
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        var pullbackX = 0f
        var pullbackY = 0f

        // 水平越界：图片左边缘跑到 view 内部（右侧白边）或右边缘跑到 view 内部（左侧白边）
        if (rect.width() > viewW) {
            when {
                rect.left > 0f -> pullbackX = -rect.left * (1f - OVERSCROLL_DAMPING)
                rect.right < viewW -> pullbackX = (viewW - rect.right) * (1f - OVERSCROLL_DAMPING)
            }
        }
        if (rect.height() > viewH) {
            when {
                rect.top > 0f -> pullbackY = -rect.top * (1f - OVERSCROLL_DAMPING)
                rect.bottom < viewH -> pullbackY = (viewH - rect.bottom) * (1f - OVERSCROLL_DAMPING)
            }
        }

        if (pullbackX != 0f || pullbackY != 0f) {
            drawMatrix.postTranslate(pullbackX, pullbackY)
            imageMatrix = drawMatrix
        }
    }

    private fun cancelBoundAnimation() {
        boundAnimator?.cancel()
        boundAnimator = null
    }

    // ── 边界检测 ──────────────────────────────────────────────────────────

    /**
     * 判断放大后的图片在水平方向上是否还能沿 [dx] 方向继续平移。
     *
     * @param dx 手指拖动 delta（正=右拖，负=左拖）
     * @return true 表示图片还可以在该方向平移
     */
    private fun canScrollHorizontally(dx: Float): Boolean {
        val rect = currentDisplayRect() ?: return false
        val viewW = width.toFloat()
        if (rect.width() <= viewW) return false
        return when {
            dx > 0f -> rect.left < 0f   // 向右拖：左边还有内容可看
            dx < 0f -> rect.right > viewW // 向左拖：右边还有内容可看
            else -> false
        }
    }

    /**
     * 判断放大后的图片在垂直方向上是否还能沿 [dy] 方向继续平移。
     *
     * @param dy 手指拖动 delta（正=下拖，负=上拖）
     * @return true 表示图片还可以在该方向平移
     */
    private fun canScrollVertically(dy: Float): Boolean {
        val rect = currentDisplayRect() ?: return false
        val viewH = height.toFloat()
        if (rect.height() <= viewH) return false
        return when {
            dy > 0f -> rect.top < 0f    // 向下拖：上边还有内容可看
            dy < 0f -> rect.bottom > viewH // 向上拖：下边还有内容可看
            else -> false
        }
    }

    private fun currentDisplayRect(): RectF? {
        val d = drawable ?: return null
        displayRect.set(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        drawMatrix.mapRect(displayRect)
        return displayRect
    }

    private fun animateToScale(target: Float, pivotX: Float, pivotY: Float) {
        val startScale = currentScale()
        cancelBoundAnimation()
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
                override fun onAnimationEnd(animation: Animator) {
                    onScalingChanged?.invoke(false)
                    // 动画回弹：双击后位置可能有偏移
                    snapToBounds(animated = true)
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
        // 先将事件传给 ScaleGestureDetector（双指缩放）
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerCount = 1
                multiTouchInCurrentSequence = false
                isDragging = false
                lastTouchX = event.x
                lastTouchY = event.y
                // 取消进行中的回弹动画，让手指立即接管
                cancelBoundAnimation()
                if (isZoomed) {
                    // 放大状态：预先阻止 ViewPager2 拦截，优先由本 View 处理拖拽
                    requestDisallowParentIntercept(true)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                activePointerCount = event.pointerCount
                multiTouchInCurrentSequence = true
                isDragging = false
                cancelBoundAnimation()
                requestDisallowParentIntercept(true)
                cancelGestureDetection(event)
                cancelLongPressReleaseForMultiTouch()
            }
            MotionEvent.ACTION_MOVE -> {
                if (isZoomed && activePointerCount == 1 && !scaleDetector.isInProgress && !isPinching) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    val absDx = abs(dx)
                    val absDy = abs(dy)

                    if (absDx > 0f || absDy > 0f) {
                        isDragging = true
                        val isHorizontalDrag = absDx > absDy
                        val atHorizontalEdge = isHorizontalDrag && !canScrollHorizontally(dx)

                        if (atHorizontalEdge) {
                            // 图片水平已到边界，且当前为主水平拖拽 → 释放拦截权，让 ViewPager2 切页
                            requestDisallowParentIntercept(false)
                        } else {
                            // 还能在该方向平移（或主垂直拖拽） → 自己消费，平移图片
                            requestDisallowParentIntercept(true)
                            // 1. 先完整跟随手指
                            drawMatrix.postTranslate(dx, dy)
                            imageMatrix = drawMatrix
                            // 2. 再施加越界阻尼（橡皮筋手感）
                            applyOverscrollResistance()
                        }
                    }

                    lastTouchX = event.x
                    lastTouchY = event.y
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                activePointerCount = (event.pointerCount - 1).coerceAtLeast(0)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerCount = event.pointerCount
                if (isDragging && isZoomed) {
                    // 拖拽结束：动画回弹到合法边界
                    snapToBounds(animated = true)
                }
            }
        }

        // 单指且无缩放/多指历史/拖拽时才允许 GestureDetector 处理（单击、双击、长按）
        val allowSingleFingerTap = event.pointerCount <= 1 &&
            !scaleDetector.isInProgress &&
            !isPinching &&
            !multiTouchInCurrentSequence &&
            !isDragging
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
            isDragging = false
            lastTouchX = event.x
            lastTouchY = event.y
        } else if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            activePointerCount = 0
            multiTouchInCurrentSequence = false
            isDragging = false
            lastTouchX = event.x
            lastTouchY = event.y
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
