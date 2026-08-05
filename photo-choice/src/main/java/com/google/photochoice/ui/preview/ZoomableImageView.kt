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
import androidx.appcompat.widget.AppCompatImageView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import kotlin.math.abs

/**
 * 大图预览缩放视图。
 *
 * - 初始：按 [resolveImagePreviewFitMode] 判定结果展示——正常图片 fit-center 居中；
 *   长图（偏竖且按高适配后显示宽度达到屏宽阈值）整宽显示、顶部对齐
 * - 长图未放大时也可单指上下滑动浏览完整内容，见 [hasVerticalOverflow]
 * - 双指 pinch：1x ~ 3x，以双指中点为轴心并跟随中点平移，越界有阻尼回弹
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
        private const val ANIMATE_DURATION = 300L
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
    /** [isMatrixNearlyEqual] 的复用缓冲，避免每次比较都新建数组。 */
    private val matrixCompareA = FloatArray(9)
    private val matrixCompareB = FloatArray(9)

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
    /**
     * 当前进行中的矩阵动画（双击缩放 / 越界回弹）。二者共用一个字段，保证任何时候
     * 只有一个动画在改写 [drawMatrix]——手指按下或新动画启动时先取消上一个，
     * 避免两个动画（或动画与手指拖拽）同时写矩阵造成画面抖动。
     */
    private var activeAnimator: ValueAnimator? = null
    /**
     * pinch 期间上一帧的双指中点，用于让图片跟随中点移动，见 [scaleDetector] 的 onScale。
     */
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    /**
     * 指针数刚发生变化（按下/抬起第三指等）时置位：该帧的双指中点会因参与平均的指针
     * 集合改变而突跳，若照常按中点位移补偿平移会导致图片瞬移，故跳过一帧。
     */
    private var skipNextFocusDelta = false

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
                cancelMatrixAnimation()
                requestDisallowParentIntercept(true)
                // 记录起始中点，后续每帧按中点位移同步平移图片
                lastFocusX = detector.focusX
                lastFocusY = detector.focusY
                skipNextFocusDelta = false
                onScalingChanged?.invoke(true)
                return true
            }

            /**
             * 每帧同时处理两件事：
             * 1. 以当前双指中点为轴心施加（阻尼后的）缩放；
             * 2. 按中点自身的位移平移图片。
             *
             * 第 2 步是消除抖动的关键：两指移动很难完全对称，中点每帧都在漂移。若只用
             * 中点当缩放轴心而不补偿其位移，等于每帧换一个轴心去缩放，位置误差逐帧累积
             * 表现为画面摆动；同时双指整体平移（两指同向移动、间距不变）时图片会完全
             * 不跟手。补上中点位移后，缩放中心与手指严格绑定，画面稳定且跟手。
             */
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val focusX = detector.focusX
                val focusY = detector.focusY

                // 指针集合刚变化的那一帧中点会突跳，丢弃其位移，只保留缩放
                val focusDx = if (skipNextFocusDelta) 0f else focusX - lastFocusX
                val focusDy = if (skipNextFocusDelta) 0f else focusY - lastFocusY
                skipNextFocusDelta = false
                lastFocusX = focusX
                lastFocusY = focusY

                val cur = currentScale()
                val dampedFactor = applyScaleDamping(cur, detector.scaleFactor)
                if (dampedFactor != 1f) {
                    drawMatrix.postScale(dampedFactor, dampedFactor, focusX, focusY)
                }
                if (focusDx != 0f || focusDy != 0f) {
                    // 中点位移同样受越界阻尼约束，避免放大越界时双指平移把图片拖飞
                    val rectBeforeMove = currentDisplayRect()
                    val dampedDx = dampOverscrollDelta(
                        delta = focusDx,
                        overshoot = horizontalOvershoot(rectBeforeMove, focusDx),
                        maxOvershoot = width * MAX_OVERSCROLL_DRAG_RATIO
                    )
                    val dampedDy = dampOverscrollDelta(
                        delta = focusDy,
                        overshoot = verticalOvershoot(rectBeforeMove, focusDy),
                        maxOvershoot = height * MAX_OVERSCROLL_DRAG_RATIO
                    )
                    drawMatrix.postTranslate(dampedDx, dampedDy)
                }
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
     * @param animated true 时动画过渡（对起止矩阵统一插值，见 [animateMatrixTo]），
     *   false 时立即修正。
     */
    private fun snapToBounds(animated: Boolean) {
        cancelMatrixAnimation()

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (viewW <= 0f || viewH <= 0f) return

        // 缩放先夹回 [MIN_SCALE, MAX_SCALE]，再按夹回后的尺寸算位移修正，
        // 由 computeScaledAndClampedMatrix 一步给出合法的目标矩阵。
        val cur = currentScale()
        val targetScale = cur.coerceIn(MIN_SCALE, MAX_SCALE)
        val target = computeScaledAndClampedMatrix(targetScale, viewW / 2f, viewH / 2f)

        if (isMatrixNearlyEqual(drawMatrix, target)) return

        if (!animated) {
            drawMatrix.set(target)
            imageMatrix = drawMatrix
            return
        }
        animateMatrixTo(target, BOUND_ANIM_DURATION)
    }

    /**
     * 将 [drawMatrix] 在 [duration] 内平滑过渡到 [target]。
     *
     * 对起止矩阵的九个值统一线性插值，而非"每帧算相对增量再 postScale/postTranslate 叠加"——
     * 后者在帧间隔不均匀或浮点误差累积时速度不均匀，是回弹/缩放动画发涩发抖的根源。
     * 动画句柄统一存入 [activeAnimator]，任何新动画或手指按下都会先取消它。
     */
    private fun animateMatrixTo(target: Matrix, duration: Long, onEnd: (() -> Unit)? = null) {
        val startValues = FloatArray(9)
        drawMatrix.getValues(startValues)
        val endValues = FloatArray(9)
        target.getValues(endValues)
        val frameValues = FloatArray(9)

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            // 先加速后减速，比全程匀减速更接近真实物体运动，视觉更顺滑
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { va ->
                val fraction = va.animatedFraction
                for (i in 0 until 9) {
                    frameValues[i] = startValues[i] + (endValues[i] - startValues[i]) * fraction
                }
                drawMatrix.setValues(frameValues)
                imageMatrix = drawMatrix
            }
            addListener(object : AnimatorListenerAdapter() {
                private var canceled = false

                override fun onAnimationCancel(animation: Animator) {
                    // 被取消时保留当前帧矩阵，交给手指或新动画接管，不强行跳到终点
                    canceled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!canceled) {
                        // 精确落到终点矩阵，消除逐帧插值的累积误差
                        drawMatrix.setValues(endValues)
                        imageMatrix = drawMatrix
                    }
                    if (activeAnimator === animation) activeAnimator = null
                    if (!canceled) onEnd?.invoke()
                }
            })
        }
        activeAnimator = anim
        anim.start()
    }

    /** 两个矩阵是否已足够接近（无需再做修正动画）。 */
    private fun isMatrixNearlyEqual(a: Matrix, b: Matrix): Boolean {
        a.getValues(matrixCompareA)
        b.getValues(matrixCompareB)
        for (i in 0 until 9) {
            if (abs(matrixCompareA[i] - matrixCompareB[i]) > 0.001f) return false
        }
        return true
    }

    /** 取消进行中的矩阵动画（双击缩放或越界回弹），让手指立即接管。 */
    private fun cancelMatrixAnimation() {
        activeAnimator?.cancel()
        activeAnimator = null
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

    /**
     * 双击缩放动画：从当前矩阵平滑过渡到"以 [pivotX]/[pivotY] 为中心放大到 [target] 倍数、
     * 并已修正越界位移"后的最终矩阵。
     *
     * 动画终点（[computeScaledAndClampedMatrix]）预先一次性算好，过渡交给
     * [animateMatrixTo] 做矩阵统一插值。这样缩放与位移修正在同一段动画内同步完成，
     * 不会出现"先放大到越界、动画结束后再另起一段回弹挪回"的两段式割裂感。
     */
    private fun animateToScale(target: Float, pivotX: Float, pivotY: Float) {
        cancelMatrixAnimation()
        requestDisallowParentIntercept(true)
        onScalingChanged?.invoke(true)

        val endMatrix = computeScaledAndClampedMatrix(target, pivotX, pivotY)
        animateMatrixTo(endMatrix, ANIMATE_DURATION) {
            onScalingChanged?.invoke(false)
            notifyZoomState()
            if (!isZoomed) {
                requestDisallowParentIntercept(false)
            }
        }
    }

    /**
     * 计算"以 [pivotX]/[pivotY] 为中心将当前矩阵缩放到 [target] 倍数，并修正越界位移后"
     * 的目标矩阵，不修改 [drawMatrix]，仅用于给 [animateToScale] 提供动画终点。
     *
     * 位移修正逻辑与 [snapToBounds] 一致（居中或贴边），但这里一步算出最终结果而非
     * 动画到边界之外再二次修正，避免双击动画结束后再触发一次可感知的回弹跳动。
     */
    private fun computeScaledAndClampedMatrix(target: Float, pivotX: Float, pivotY: Float): Matrix {
        val result = Matrix(drawMatrix)
        val cur = currentScale()
        val factor = if (cur > 0f) target / cur else 1f
        if (factor != 1f) {
            result.postScale(factor, factor, pivotX, pivotY)
        }

        // 用局部 RectF 而非共享的 displayRect：后者由 currentDisplayRect() 以引用形式
        // 返回给调用方复用，此处若改写它会污染调用方持有的 rect。
        val rect = RectF()
        val d = drawable
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (d != null && viewW > 0f && viewH > 0f) {
            rect.set(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
            result.mapRect(rect)

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
            if (correctionDx != 0f || correctionDy != 0f) {
                result.postTranslate(correctionDx, correctionDy)
            }
        }
        return result
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

    /**
     * 在 [MotionEvent.ACTION_POINTER_UP] 时把 [lastTouchX]/[lastTouchY] 同步到"抬手后仍留在
     * 屏幕上"的那根手指的坐标。
     *
     * 该事件的 `event.x/y` 是**正在抬起**那根手指的坐标，直接取用会让后续单指拖拽以错误
     * 的基准计算 delta。因此按 [MotionEvent.getActionIndex] 跳过抬起的指针，取第一根仍在
     * 屏幕上的指针坐标；若已无剩余指针则保持原值（马上就会收到 ACTION_UP）。
     */
    private fun syncLastTouchToRemainingPointer(event: MotionEvent) {
        val upIndex = event.actionIndex
        for (i in 0 until event.pointerCount) {
            if (i == upIndex) continue
            lastTouchX = event.getX(i)
            lastTouchY = event.getY(i)
            return
        }
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
                // 取消进行中的缩放/回弹动画，让手指立即接管
                cancelMatrixAnimation()
                if (isZoomed || hasVerticalOverflow()) {
                    // 放大状态或长图纵向溢出：预先阻止 ViewPager2 拦截，优先由本 View 处理拖拽
                    requestDisallowParentIntercept(true)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                activePointerCount = event.pointerCount
                multiTouchInCurrentSequence = true
                isDragging = false
                // 指针数变化会让 ScaleGestureDetector 的中点突跳，下一帧只取缩放不取位移
                skipNextFocusDelta = true
                cancelMatrixAnimation()
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
                        // 手指开始拖拽 → 取消进行中的回弹/缩放动画，否则动画与拖拽同时
                        // 改写 drawMatrix 会互相覆盖，表现为画面抖动（多指抬起后转为单指
                        // 拖拽时最易出现：ACTION_POINTER_UP 尚未结束整个手势，但 onScaleEnd
                        // 已经启动了回弹动画）。
                        cancelMatrixAnimation()
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
                // 指针数变化后中点会突跳，下一帧 pinch 只取缩放不取位移
                skipNextFocusDelta = true
                // pinch 期间 lastTouchX/Y 一直没更新（拖拽分支被 isPinching 挡住），
                // 若不在此处同步到"剩余手指"的坐标，抬起一指后的第一帧 ACTION_MOVE 会用
                // pinch 之前的旧坐标算出一个巨大 delta，导致图片瞬间跳一下。
                syncLastTouchToRemainingPointer(event)
                // 剩余单指接管前重置累计拖拽起点，否则 pinch 前的起点会让方向判断失真
                dragStartX = lastTouchX
                dragStartY = lastTouchY
                isDragging = false
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
