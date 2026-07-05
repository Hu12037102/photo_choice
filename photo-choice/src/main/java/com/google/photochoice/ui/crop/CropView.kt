package com.google.photochoice.ui.crop

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.withSave
import com.google.photochoice.R
import com.google.photochoice.config.CropAspectRatio
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 裁剪视图（交互对齐微信）：
 * - 裁剪框固定居中；单指平移 / 双指仅缩放（锚点为裁剪框中心，帧间插值更丝滑）
 * - 最小缩放：图片宽度与裁剪框等宽，左右边始终对齐裁剪框
 * - 双指缩放过程中允许略小于最小宽度（越界预览），松手后动画回弹并恢复左右贴边
 * - 大于最小缩放时：图片必须完全覆盖裁剪框
 */
class CropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    var aspectRatio: CropAspectRatio = CropAspectRatio.ORIGINAL
        set(value) {
            field = value
            recalcCropRect()
            fitImageToCrop()
            invalidate()
        }

    private val imageMatrixInternal = Matrix()
    private val snapBaseMatrix = Matrix()
    private val pinchBaseMatrix = Matrix()
    private val zoomStartMatrix = Matrix()
    private val pinchBaseDisplayRect = RectF()
    private val cropRect = RectF()
    private val displayRect = RectF()
    private val tempValues = FloatArray(9)

    /** 图片刚好铺满裁剪框（cover）时的缩放比。 */
    private var coverScale = 1f

    private var snapAnimator: ValueAnimator? = null
    private var snapStartScale = 1f

    /** 当前是否为多指触控（多指期间禁止单指平移）。 */
    private var multiTouchActive = false

    /** 双指缩放：手势目标 / 当前渲染缩放比。 */
    private var pinchActive = false
    private var pinchFrameScheduled = false
    private var pinchBaseScale = 1f
    private var pinchTargetScale = 1f
    private var pinchDisplayScale = 1f

    /** 双指缩放已结束但仍有手指在屏上，延后到全部抬起再回弹。 */
    private var pendingPinchSettle = false

    private val pinchFrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!pinchActive) {
                pinchFrameScheduled = false
                return
            }
            val diff = pinchTargetScale - pinchDisplayScale
            pinchDisplayScale = if (abs(diff) > SCALE_RENDER_EPSILON) {
                pinchDisplayScale + diff * PINCH_LERP_FACTOR
            } else {
                pinchTargetScale
            }
            applyPinchDisplayScale(pinchDisplayScale)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val blackFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private val maskPaint by lazy {
        Paint().apply {
            color = ContextCompat.getColor(context, R.color.photochoice_scrim)
            style = Paint.Style.FILL
        }
    }
    private val borderPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * context.resources.displayMetrics.density
        }
    }

    private val scaleDetector =
        ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                cancelScaleAnimations()
                pendingPinchSettle = false
                pinchBaseMatrix.set(imageMatrixInternal)
                pinchBaseScale = currentScale().coerceAtLeast(SCALE_EPSILON)
                pinchTargetScale = pinchBaseScale
                pinchDisplayScale = pinchBaseScale
                pinchActive = true
                startPinchSmoothLoop()
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                // 缩放过程中仅限制上限与越界下限，真正最小边界在松手后回弹
                pinchTargetScale = (pinchTargetScale * detector.scaleFactor)
                    .coerceIn(pinchOvershootMinScale(), maxScale())
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                // 仅停止插值并冻结当前越界画面；回弹必须等所有手指离开屏幕
                stopPinchSmoothLoop()
                applyPinchDisplayScale(pinchDisplayScale)
                pendingPinchSettle = true
            }
        })

    private val gestureDetector =
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float
            ): Boolean {
                cancelScaleAnimations()
                imageMatrixInternal.postTranslate(-dx, -dy)
                constrainTranslation()
                applyMatrix()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                handleDoubleTap(e.x, e.y)
                return true
            }
        })

    init {
        scaleType = ScaleType.MATRIX
        setBackgroundColor(Color.BLACK)
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        recalcCropRect()
        fitImageToCrop()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalcCropRect()
        fitImageToCrop()
    }

    override fun onDetachedFromWindow() {
        cancelScaleAnimations()
        super.onDetachedFromWindow()
    }

    /**
     * 根据当前比例重新计算居中裁剪框区域。
     */
    private fun recalcCropRect() {
        val vw = width.toFloat()
        val vh = height.toFloat()
        if (vw <= 0f || vh <= 0f) return

        val maxW = vw * 0.88f
        val maxH = vh * 0.7f

        val targetRatio: Float = if (aspectRatio == CropAspectRatio.ORIGINAL) {
            val d = drawable ?: run {
                cropRect.set(
                    (vw - maxW) / 2f, (vh - maxH) / 2f,
                    (vw + maxW) / 2f, (vh + maxH) / 2f
                )
                return
            }
            if (d.intrinsicHeight <= 0) 1f
            else d.intrinsicWidth.toFloat() / d.intrinsicHeight.toFloat()
        } else {
            aspectRatio.ratio ?: 1f
        }

        var cw = maxW
        var ch = cw / targetRatio
        if (ch > maxH) {
            ch = maxH
            cw = ch * targetRatio
        }
        cropRect.set(
            (vw - cw) / 2f, (vh - ch) / 2f,
            (vw + cw) / 2f, (vh + ch) / 2f
        )
        updateCoverScale()
    }

    /**
     * 初始状态：图片以 cover 方式铺满裁剪框。
     */
    private fun fitImageToCrop() {
        val d = drawable ?: return
        val iw = d.intrinsicWidth.toFloat()
        val ih = d.intrinsicHeight.toFloat()
        if (iw <= 0f || ih <= 0f) return

        updateCoverScale()
        val scale = coverScale
        val tx = cropRect.centerX() - iw * scale / 2f
        val ty = cropRect.centerY() - ih * scale / 2f
        imageMatrixInternal.reset()
        imageMatrixInternal.postScale(scale, scale)
        imageMatrixInternal.postTranslate(tx, ty)
        applyMatrix()
    }

    /** 计算图片刚好覆盖裁剪框（cover）所需的缩放比。 */
    private fun updateCoverScale() {
        val d = drawable ?: run {
            coverScale = 1f
            return
        }
        val iw = d.intrinsicWidth.toFloat()
        val ih = d.intrinsicHeight.toFloat()
        if (iw <= 0f || ih <= 0f || cropRect.isEmpty) {
            coverScale = 1f
            return
        }
        coverScale = max(cropRect.width() / iw, cropRect.height() / ih)
    }

    /**
     * 最小缩放：图片宽度与裁剪框等宽（左右边对齐裁剪框）。
     * 永远不大于 coverScale。
     */
    private fun minScale(): Float {
        val d = drawable ?: return coverScale
        val iw = d.intrinsicWidth.toFloat()
        if (iw <= 0f) return coverScale
        return cropRect.width() / iw
    }

    /** 最大缩放：cover 的 5 倍。 */
    private fun maxScale(): Float = coverScale * MAX_SCALE_FACTOR

    /** 启动双指缩放帧间插值循环。 */
    private fun startPinchSmoothLoop() {
        if (pinchFrameScheduled) return
        pinchFrameScheduled = true
        Choreographer.getInstance().postFrameCallback(pinchFrameCallback)
    }

    /** 停止双指缩放帧间插值循环。 */
    private fun stopPinchSmoothLoop() {
        pinchActive = false
    }

    /** 取消所有缩放相关动画。 */
    private fun cancelScaleAnimations() {
        snapAnimator?.cancel()
        snapAnimator = null
        pendingPinchSettle = false
        stopPinchSmoothLoop()
    }

    /**
     * 全部手指抬起后执行：从越界缩放动画回到合法范围并修正贴边。
     */
    private fun settlePinchIfPending() {
        if (!pendingPinchSettle) return
        pendingPinchSettle = false
        settlePinchScale()
    }

    /** 是否正在执行缩放动画（含双指 settle / 回弹 / 双击缩放）。 */
    private fun isScaleAnimating(): Boolean {
        return pinchActive || snapAnimator?.isRunning == true
    }

    /** 双指缩小越界预览下限（相对 minScale 的比例）。 */
    private fun pinchOvershootMinScale(): Float = minScale() * PINCH_OVERSHOOT_MIN_RATIO

    /**
     * 按 pinch 基准矩阵 + 目标缩放比渲染。
     * 锚点 = 会话开始时图片显示中心（非裁剪框中心），避免最小贴边态缩放时视图漂移。
     */
    private fun applyPinchDisplayScale(displayScale: Float) {
        if (pinchBaseScale <= SCALE_EPSILON) return
        val d = drawable ?: return
        imageMatrixInternal.set(pinchBaseMatrix)
        val factor = displayScale / pinchBaseScale
        pinchBaseDisplayRect.set(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        pinchBaseMatrix.mapRect(pinchBaseDisplayRect)
        imageMatrixInternal.postScale(
            factor, factor,
            pinchBaseDisplayRect.centerX(), pinchBaseDisplayRect.centerY()
        )
        applyMatrix()
    }

    /**
     * 双指松手：动画落到合法缩放比（小于 min 则回弹到 min），再修正边界。
     */
    private fun settlePinchScale() {
        val start = pinchDisplayScale
        val end = pinchTargetScale.coerceIn(minScale(), maxScale())
        if (abs(start - end) < SCALE_EPSILON) {
            applyPinchDisplayScale(end)
            finishPinchSession()
            return
        }
        val duration = if (start < minScale() - SCALE_EPSILON) SNAP_DURATION_MS else PINCH_SETTLE_MS
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofFloat(start, end).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                applyPinchDisplayScale(anim.animatedValue as Float)
            }
            doOnEnd { finishPinchSession() }
            start()
        }
    }

    /** 双指缩放结束：修正边界；若仍小于 min 则触发回弹动画。 */
    private fun finishPinchSession() {
        val d = drawable ?: return
        if (currentScale() < minScale() - SCALE_EPSILON) {
            animateSpringToMinScale(d)
            return
        }
        constrainTranslation()
        applyMatrix()
    }

    /**
     * 带动画回弹到最小宽度对齐态（左右贴边）。
     */
    private fun animateSpringToMinScale(d: Drawable) {
        val target = minScale()
        val current = currentScale()
        if (current >= target - SCALE_EPSILON) {
            constrainAtMinScale(d)
            applyMatrix()
            return
        }
        snapAnimator?.cancel()
        snapBaseMatrix.set(imageMatrixInternal)
        snapStartScale = current
        snapAnimator = ValueAnimator.ofFloat(current, target).apply {
            duration = SNAP_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val desiredScale = anim.animatedValue as Float
                imageMatrixInternal.set(snapBaseMatrix)
                if (snapStartScale <= SCALE_EPSILON) return@addUpdateListener
                val factor = desiredScale / snapStartScale
                pinchBaseDisplayRect.set(
                    0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat()
                )
                snapBaseMatrix.mapRect(pinchBaseDisplayRect)
                imageMatrixInternal.postScale(
                    factor, factor,
                    pinchBaseDisplayRect.centerX(), pinchBaseDisplayRect.centerY()
                )
                constrainAtMinScale(d)
                applyMatrix()
            }
            doOnEnd {
                constrainAtMinScale(d)
                applyMatrix()
            }
            start()
        }
    }

    /**
     * 带动画缩放到目标比例（双击等），缩放结束后修正边界。
     */
    private fun animateZoomTo(
        targetScale: Float,
        pivotX: Float,
        pivotY: Float,
        durationMs: Long = ZOOM_ANIM_DURATION_MS
    ) {
        val startScale = currentScale()
        if (startScale <= SCALE_EPSILON) return
        val endScale = targetScale.coerceIn(minScale(), maxScale())
        if (abs(startScale - endScale) < SCALE_EPSILON) {
            finishPinchSession()
            return
        }
        zoomStartMatrix.set(imageMatrixInternal)
        cancelScaleAnimations()
        snapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                val scale = startScale + (endScale - startScale) * t
                imageMatrixInternal.set(zoomStartMatrix)
                imageMatrixInternal.postScale(
                    scale / startScale, scale / startScale, pivotX, pivotY
                )
                applyMatrix()
            }
            doOnEnd { finishPinchSession() }
            start()
        }
    }

    /**
     * 双击：已放大则回到 cover；否则以点击点为中心放大 2 倍。
     */
    private fun handleDoubleTap(focusX: Float, focusY: Float) {
        val current = currentScale()
        val target = if (current > coverScale * DOUBLE_TAP_COVER_EPSILON) {
            coverScale
        } else {
            (current * DOUBLE_TAP_ZOOM_FACTOR).coerceAtMost(maxScale())
        }
        animateZoomTo(target, focusX, focusY)
    }

    /**
     * 松手时若小于最小缩放，带动画回弹到「宽度对齐」状态。
     */
    private fun springBackToMinScaleIfNeeded() {
        val d = drawable ?: return
        if (currentScale() >= minScale() - SCALE_EPSILON) {
            constrainTranslation()
            applyMatrix()
            return
        }
        animateSpringToMinScale(d)
    }

    /**
     * 修正缩放与平移：
     * - 处于最小缩放：宽度对齐裁剪框，仅允许垂直方向平移
     * - 大于最小缩放：四边必须覆盖裁剪框
     */
    private fun constrainTranslation() {
        val d = drawable ?: return
        clampScaleToBounds()
        mapDisplayRect(d)

        val scale = currentScale()
        when {
            scale <= minScale() + WIDTH_ALIGN_EPSILON -> constrainAtMinScale(d)
            scale >= coverScale - SCALE_EPSILON -> constrainFullCover()
            else -> constrainBetweenMinAndCover()
        }
    }

    /** 将缩放限制在 [minScale, maxScale]。 */
    private fun clampScaleToBounds() {
        val min = minScale()
        val max = maxScale()
        val current = currentScale()
        when {
            current < min - SCALE_EPSILON -> {
                val factor = min / current
                imageMatrixInternal.postScale(factor, factor, cropRect.centerX(), cropRect.centerY())
            }
            current > max + SCALE_EPSILON -> {
                val factor = max / current
                imageMatrixInternal.postScale(factor, factor, cropRect.centerX(), cropRect.centerY())
            }
        }
    }

    /**
     * 最小缩放态：左右边与裁剪框对齐，高度不足时垂直居中，高度超出时限制上下平移。
     */
    private fun constrainAtMinScale(d: Drawable) {
        val min = minScale()
        val current = currentScale()
        if (abs(current - min) > SCALE_EPSILON) {
            val factor = min / current
            imageMatrixInternal.postScale(factor, factor, cropRect.centerX(), cropRect.centerY())
            mapDisplayRect(d)
        }

        // 宽度精确对齐裁剪框（两边贴齐）
        val widthFix = cropRect.width() / displayRect.width()
        if (abs(widthFix - 1f) > WIDTH_ALIGN_EPSILON) {
            imageMatrixInternal.postScale(widthFix, widthFix, cropRect.centerX(), cropRect.centerY())
            mapDisplayRect(d)
        }
        val dx = cropRect.left - displayRect.left
        if (abs(dx) > 0.5f) {
            imageMatrixInternal.postTranslate(dx, 0f)
            mapDisplayRect(d)
        }

        var dy = 0f
        if (displayRect.height() >= cropRect.height() - 0.5f) {
            if (displayRect.top > cropRect.top) {
                dy = cropRect.top - displayRect.top
            }
            if (displayRect.bottom < cropRect.bottom) {
                dy = cropRect.bottom - displayRect.bottom
            }
        } else {
            dy = cropRect.centerY() - displayRect.centerY()
        }
        if (abs(dy) > 0.5f) {
            imageMatrixInternal.postTranslate(0f, dy)
        }
    }

    /** cover 态：四边独立约束，图片必须完全覆盖裁剪框。 */
    private fun constrainFullCover() {
        var dx = 0f
        var dy = 0f
        if (displayRect.left > cropRect.left) {
            dx = cropRect.left - displayRect.left
        }
        if (displayRect.right < cropRect.right) {
            dx = cropRect.right - displayRect.right
        }
        if (displayRect.top > cropRect.top) {
            dy = cropRect.top - displayRect.top
        }
        if (displayRect.bottom < cropRect.bottom) {
            dy = cropRect.bottom - displayRect.bottom
        }
        if (dx != 0f || dy != 0f) {
            imageMatrixInternal.postTranslate(dx, dy)
        }
    }

    /**
     * 介于最小缩放与 cover 之间：宽度仍大于裁剪框时可左右平移；
     * 高度不足时出现上下黑边并垂直居中。
     */
    private fun constrainBetweenMinAndCover() {
        var dx = 0f
        var dy = 0f
        if (displayRect.width() >= cropRect.width()) {
            if (displayRect.left > cropRect.left) {
                dx = cropRect.left - displayRect.left
            }
            if (displayRect.right < cropRect.right) {
                dx = cropRect.right - displayRect.right
            }
        }
        if (displayRect.height() >= cropRect.height() - 0.5f) {
            if (displayRect.top > cropRect.top) {
                dy = cropRect.top - displayRect.top
            }
            if (displayRect.bottom < cropRect.bottom) {
                dy = cropRect.bottom - displayRect.bottom
            }
        } else {
            dy = cropRect.centerY() - displayRect.centerY()
        }
        if (dx != 0f || dy != 0f) {
            imageMatrixInternal.postTranslate(dx, dy)
        }
    }

    /** 将 drawable 原始边界映射到当前屏幕坐标。 */
    private fun mapDisplayRect(d: Drawable) {
        displayRect.set(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        imageMatrixInternal.mapRect(displayRect)
    }

    private fun currentScale(): Float {
        imageMatrixInternal.getValues(tempValues)
        val sx = tempValues[Matrix.MSCALE_X]
        val ky = tempValues[Matrix.MSKEW_Y]
        return sqrt(sx * sx + ky * ky)
    }

    private fun applyMatrix() {
        imageMatrix = imageMatrixInternal
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) multiTouchActive = true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount - 1 < 2) multiTouchActive = false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                multiTouchActive = false
            }
        }

        scaleDetector.onTouchEvent(event)

        // 单指且非多指/非缩放中/非等待松手回弹：才允许平移
        val allowPan = event.pointerCount == 1 &&
            !multiTouchActive &&
            !scaleDetector.isInProgress &&
            !pinchActive &&
            !isScaleAnimating()
        if (allowPan) {
            gestureDetector.onTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // 双指缩放越界：必须等最后一根手指抬起才回弹
                settlePinchIfPending()
                if (!pendingPinchSettle && !scaleDetector.isInProgress && !isScaleAnimating()) {
                    springBackToMinScaleIfNeeded()
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(cropRect, blackFillPaint)
        super.onDraw(canvas)
        canvas.withSave {
            clipOutRect(cropRect)
            drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint)
        }
        canvas.drawRect(cropRect, borderPaint)
    }

    /**
     * 执行裁剪：输出与裁剪框等大的 Bitmap，未覆盖区域填黑。
     */
    fun crop(): Bitmap? {
        val sourceBitmap = (drawable as? BitmapDrawable)?.bitmap ?: return null
        if (sourceBitmap.width <= 0 || sourceBitmap.height <= 0) return null

        val outW = cropRect.width().toInt()
        val outH = cropRect.height().toInt()
        if (outW <= 0 || outH <= 0) return null

        val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.BLACK)

        val drawMatrix = Matrix(imageMatrixInternal)
        drawMatrix.postTranslate(-cropRect.left, -cropRect.top)
        canvas.drawBitmap(sourceBitmap, drawMatrix, null)
        return result
    }

    /** ValueAnimator 结束回调（API 24 以下兼容写法）。 */
    private inline fun ValueAnimator.doOnEnd(crossinline action: () -> Unit) {
        addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) = Unit
            override fun onAnimationCancel(animation: android.animation.Animator) = Unit
            override fun onAnimationRepeat(animation: android.animation.Animator) = Unit
            override fun onAnimationEnd(animation: android.animation.Animator) {
                action()
            }
        })
    }

    companion object {
        private const val MAX_SCALE_FACTOR = 5f
        private const val DOUBLE_TAP_ZOOM_FACTOR = 2f
        private const val DOUBLE_TAP_COVER_EPSILON = 1.05f
        private const val SCALE_EPSILON = 0.001f
        private const val SCALE_RENDER_EPSILON = 0.0008f
        private const val WIDTH_ALIGN_EPSILON = 0.002f
        /** 双指缩放每帧向目标缩放比靠拢的比例，越大越跟手、越小越丝滑。 */
        private const val PINCH_LERP_FACTOR = 0.34f
        private const val PINCH_SETTLE_MS = 120L
        private const val ZOOM_ANIM_DURATION_MS = 220L
        private const val SNAP_DURATION_MS = 180L
        /** 最小贴边态双指缩小时的越界预览比例（松手回弹）。 */
        private const val PINCH_OVERSHOOT_MIN_RATIO = 0.88f
    }
}
