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
 * - 初始：按 [resolveImagePreviewFitMode] 判定结果展示——正常图片 fit-center 居中；
 *   长图（偏竖且按高适配后显示宽度达到屏宽阈值）整宽显示、顶部对齐
 * - 长图未放大时也可单指上下滑动浏览完整内容，见 [hasVerticalOverflow]
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
         * 拖拽越界的最大可视位移 = view 对应边长（宽/高）的此比例。越界位移越接近该
         * 上限，本帧新增位移的允许通过比例（见 [dampOverscrollDelta]）越趋于 0；越界量
         * 会随手指持续拖拽渐进逼近该上限，而不是几帧内就收敛到与拖拽距离无关的定值。
         */
        private const val MAX_OVERSCROLL_DRAG_RATIO = 0.5f
        /**
         * 缩小越界时最多可缩到初始尺寸的此倍数（0.6 = 初始的 60%）。
         */
        private const val MIN_SCALE_OVERSHOOT_RATIO = 0.4f
        /**
         * 放大越界时最多可放到最大尺寸的此比例（0.1 = MAX_SCALE * 1.1）。
         */
        private const val MAX_SCALE_OVERSHOOT_RATIO = 0.1f
    }

    private val baseMatrix = Matrix()
    private val drawMatrix = Matrix()
    private val tempValues = FloatArray(9)
    private val displayRect = RectF()

    /** 单指拖拽：记录上一次触摸位置，用于计算 delta。 */
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    /**
     * 单指拖拽起始触摸位置：用于按"本次手势累计位移"而非单帧位移判断主导拖拽方向
     * （见 [MotionEvent.ACTION_MOVE] 中 isHorizontalDrag 的计算）。手指存在自然抖动，
     * 单帧位移偶尔会在明显是垂直滑动的手势中出现 dx 略大于 dy 的噪声帧；长图整宽
     * 展示时横向可拖余量恒为 0（[canScrollHorizontally] 恒为 false），一旦某帧被误判为
     * "主导横向拖拽" 就会立即判定为"已到水平边界"而释放拦截权，导致本该丝滑的上下
     * 滑动偶发被外层 ViewPager2/BounceLayout 的默认拖拽效果接管。改用累计位移后，
     * 单帧噪声会被此前已累积的、方向明确的位移淹没，不会误判。
     */
    private var dragStartX = 0f
    private var dragStartY = 0f
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
                val dampedFactor = applyScaleDamping(cur, detector.scaleFactor)
                if (dampedFactor == 1f) return true
                drawMatrix.postScale(dampedFactor, dampedFactor, detector.focusX, detector.focusY)
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

    private fun applyBaseMatrix() {
        val d = drawable ?: return
        if (width <= 0 || height <= 0) return
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        if (dw <= 0f || dh <= 0f) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val fitMode = resolveImagePreviewFitMode(viewW, viewH, dw, dh)

        baseMatrix.reset()
        when (fitMode) {
            ImagePreviewFitMode.FIT_WIDTH_TOP_ALIGNED -> {
                // 长图：整宽显示。从原点(0,0)缩放天然顶边+左边对齐，即从图片顶部开始
                // 展示，剩余内容通过 hasVerticalOverflow() 放开的单指上下滑动查看。
                val scale = viewW / dw
                baseMatrix.setScale(scale, scale)
            }
            ImagePreviewFitMode.CENTER -> {
                val viewRect = RectF(0f, 0f, viewW, viewH)
                val drawableRect = RectF(0f, 0f, dw, dh)
                baseMatrix.setRectToRect(drawableRect, viewRect, Matrix.ScaleToFit.CENTER)
            }
        }
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

    // ── pinch 缩放越界渐近阻尼 ────────────────────────────────────────────

    /**
     * 对 pinch 缩放的 [rawFactor]（即 [ScaleGestureDetector.scaleFactor]）施加渐进阻尼。
     *
     * 与绝对位置阻尼不同，这里阻尼的是**缩放因子**，基于当前已偏离边界的程度：
     * - 刚好在边界（1x / 3x）：factor 原样通过，完全跟手
     * - 接近渐近极限（0.6x / 3.3x）：factor 趋于 1.0，几乎无法继续偏离
     * - **保证单调性**：只要手指在缩小（factor<1），阻尼后仍是缩小（dampedFactor<1），
     *   不会出现"手指缩小、图片反而放大"的反跳。
     *
     * @param cur 当前缩放倍数（已施加过阻尼的实际值）
     * @param rawFactor 手指 pinch 的原始缩放因子
     * @return 阻尼后的缩放因子
     */
    private fun applyScaleDamping(cur: Float, rawFactor: Float): Float {
        val rawTarget = cur * rawFactor

        if (rawTarget < MIN_SCALE) {
            // 越界进度：0（刚好在 1x）→ 1（在渐近极限 0.6x）
            val progress = ((MIN_SCALE - cur) / (MIN_SCALE * MIN_SCALE_OVERSHOOT_RATIO)).coerceIn(0f, 1f)
            // 进度越大阻尼越强：factor 被线性拉向 1.0
            return 1f + (rawFactor - 1f) * (1f - progress)
        }
        if (rawTarget > MAX_SCALE) {
            val progress = ((cur - MAX_SCALE) / (MAX_SCALE * MAX_SCALE_OVERSHOOT_RATIO)).coerceIn(0f, 1f)
            return 1f + (rawFactor - 1f) * (1f - progress)
        }
        return rawFactor
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
                        // 乘法插值：stepScale = S^step，确保 ∏S^step = S^1 = S 精确收敛
                        val stepScale = Math.pow(scaleCorrection.toDouble(), step.toDouble()).toFloat()
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
                            val remainingScale = Math.pow(scaleCorrection.toDouble(), remainingFraction.toDouble()).toFloat()
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

    private fun currentDisplayRect(): RectF? {
        val d = drawable ?: return null
        displayRect.set(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        drawMatrix.mapRect(displayRect)
        return displayRect
    }

    /**
     * 计算沿 [delta] 方向、本帧位移施加前已经越界的水平位移量。
     *
     * 未越界或手指正朝合法区域拖回（收窄越界）时返回 0——此时交给
     * [dampOverscrollDelta] 的增量应 1:1 跟手，不需要阻尼。委托给纯函数
     * [computeOvershoot]，语义与用法详见其文档。
     */
    private fun horizontalOvershoot(rect: RectF?, delta: Float): Float {
        rect ?: return 0f
        return computeOvershoot(rect.left, rect.right, width.toFloat(), delta)
    }

    /**
     * 计算沿 [delta] 方向、本帧位移施加前已经越界的垂直位移量，语义同
     * [horizontalOvershoot]。
     */
    private fun verticalOvershoot(rect: RectF?, delta: Float): Float {
        rect ?: return 0f
        return computeOvershoot(rect.top, rect.bottom, height.toFloat(), delta)
    }

    /**
     * 当前展示内容的高度是否超出可视区域。
     *
     * 长图整宽模式下即使尚未放大（仍是 1x 的 baseMatrix）也会超出，需要据此放开单指
     * 上下拖拽——否则只有放大状态（[isZoomed]）才允许拖拽，长图无法滑动查看全图。
     */
    private fun hasVerticalOverflow(): Boolean {
        val rect = currentDisplayRect() ?: return false
        return rect.height() > height + 0.5f
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
                dragStartX = event.x
                dragStartY = event.y
                // 取消进行中的回弹动画，让手指立即接管
                cancelBoundAnimation()
                if (isZoomed || hasVerticalOverflow()) {
                    // 放大状态或长图纵向溢出：预先阻止 ViewPager2 拦截，优先由本 View 处理拖拽
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
                if ((isZoomed || hasVerticalOverflow()) && activePointerCount == 1 && !scaleDetector.isInProgress && !isPinching) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    val absDx = abs(dx)
                    val absDy = abs(dy)

                    if (absDx > 0f || absDy > 0f) {
                        isDragging = true
                        // 主导拖拽方向按"本次手势累计位移"判断，而非单帧位移——避免手指
                        // 抖动导致的单帧噪声（明明整体是垂直滑动，个别帧 dx 略大于 dy）
                        // 被误判为主导横向拖拽，详见 dragStartX/dragStartY 字段注释。
                        val totalDx = event.x - dragStartX
                        val totalDy = event.y - dragStartY
                        val isHorizontalDrag = abs(totalDx) > abs(totalDy)
                        val atHorizontalEdge = isHorizontalDrag && !canScrollHorizontally(dx)

                        if (atHorizontalEdge) {
                            // 图片水平已到边界，且当前为主水平拖拽 → 释放拦截权，让 ViewPager2 切页
                            requestDisallowParentIntercept(false)
                        } else {
                            // 还能在该方向平移（或主垂直拖拽） → 自己消费，对越界方向的
                            // 增量先阻尼再一次性平移。阻尼基于"本帧位移施加前"的越界量，
                            // 越界量会随持续拖拽渐进逼近上限，而不是每帧都把已越界位移
                            // 拉回一部分——那样几帧内就会收敛到与拖拽距离无关的定值，
                            // 表现为"拖不动"，松手也几乎看不出回弹。
                            requestDisallowParentIntercept(true)
                            val rectBeforeDrag = currentDisplayRect()
                            val dampedDx = dampOverscrollDelta(
                                delta = dx,
                                overshoot = horizontalOvershoot(rectBeforeDrag, dx),
                                maxOvershoot = width * MAX_OVERSCROLL_DRAG_RATIO
                            )
                            val dampedDy = dampOverscrollDelta(
                                delta = dy,
                                overshoot = verticalOvershoot(rectBeforeDrag, dy),
                                maxOvershoot = height * MAX_OVERSCROLL_DRAG_RATIO
                            )
                            drawMatrix.postTranslate(dampedDx, dampedDy)
                            imageMatrix = drawMatrix
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
                if (isDragging && (isZoomed || hasVerticalOverflow())) {
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
