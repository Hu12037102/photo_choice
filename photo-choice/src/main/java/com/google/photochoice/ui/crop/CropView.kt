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
 * 裁剪视图：
 * - 单指平移 / 双指缩放；边界修正与回弹均带矩阵插值动画，避免生硬跳变
 * - 最小缩放：图片宽度与裁剪框等宽；双指可越界预览，全部手指抬起后动画回弹贴边
 */
class CropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    var aspectRatio: CropAspectRatio = CropAspectRatio.ORIGINAL
        set(value) {
            if (field == value) return
            field = value
            recalcCropRect()
            animateToFitCrop()
        }

    private val imageMatrixInternal = Matrix()
    private val pinchBaseMatrix = Matrix()
    private val zoomStartMatrix = Matrix()
    private val pinchBaseDisplayRect = RectF()
    private val cropRect = RectF()
    private val displayRect = RectF()
    private val tempMatrix = Matrix()
    private val matrixFrom = FloatArray(9)
    private val matrixTo = FloatArray(9)
    private val tempValues = FloatArray(9)

    private var coverScale = 1f

    private var matrixAnimator: ValueAnimator? = null

    private var multiTouchActive = false
    private var panGestureActive = false

    private var pinchActive = false
    private var pinchFrameScheduled = false
    private var pinchBaseScale = 1f
    private var pinchTargetScale = 1f
    private var pinchDisplayScale = 1f
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

    private val boundaryInterpolator = DecelerateInterpolator(1.6f)

    private val scaleDetector =
        ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                cancelMatrixAnimations()
                pendingPinchSettle = false
                panGestureActive = false
                pinchBaseMatrix.set(imageMatrixInternal)
                pinchBaseScale = currentScale().coerceAtLeast(SCALE_EPSILON)
                pinchTargetScale = pinchBaseScale
                pinchDisplayScale = pinchBaseScale
                pinchActive = true
                startPinchSmoothLoop()
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                pinchTargetScale = (pinchTargetScale * detector.scaleFactor)
                    .coerceIn(pinchOvershootMinScale(), maxScale())
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
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
                cancelMatrixAnimations()
                panGestureActive = true
                imageMatrixInternal.postTranslate(-dx, -dy)
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
        fitImageToCrop(animated = false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalcCropRect()
        fitImageToCrop(animated = false)
    }

    override fun onDetachedFromWindow() {
        cancelMatrixAnimations()
        super.onDetachedFromWindow()
    }

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

    /** 构建 cover 铺满裁剪框的目标矩阵。 */
    private fun buildFitCropMatrix(): Matrix {
        val result = Matrix()
        val d = drawable ?: return result
        val iw = d.intrinsicWidth.toFloat()
        val ih = d.intrinsicHeight.toFloat()
        if (iw <= 0f || ih <= 0f) return result
        updateCoverScale()
        val scale = coverScale
        val tx = cropRect.centerX() - iw * scale / 2f
        val ty = cropRect.centerY() - ih * scale / 2f
        result.postScale(scale, scale)
        result.postTranslate(tx, ty)
        return result
    }

    private fun fitImageToCrop(animated: Boolean) {
        val target = buildFitCropMatrix()
        if (animated) {
            animateMatrixTo(target, ASPECT_RATIO_ANIM_MS)
        } else {
            imageMatrixInternal.set(target)
            applyMatrix()
        }
    }

    private fun animateToFitCrop() {
        fitImageToCrop(animated = drawable != null && width > 0)
    }

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

    private fun minScale(): Float {
        val d = drawable ?: return coverScale
        val iw = d.intrinsicWidth.toFloat()
        if (iw <= 0f) return coverScale
        return cropRect.width() / iw
    }

    private fun maxScale(): Float = coverScale * MAX_SCALE_FACTOR

    private fun pinchOvershootMinScale(): Float = minScale() * PINCH_OVERSHOOT_MIN_RATIO

    private fun startPinchSmoothLoop() {
        if (pinchFrameScheduled) return
        pinchFrameScheduled = true
        Choreographer.getInstance().postFrameCallback(pinchFrameCallback)
    }

    private fun stopPinchSmoothLoop() {
        pinchActive = false
    }

    private fun cancelMatrixAnimations() {
        matrixAnimator?.cancel()
        matrixAnimator = null
        pendingPinchSettle = false
        stopPinchSmoothLoop()
    }

    private fun settlePinchIfPending() {
        if (!pendingPinchSettle) return
        pendingPinchSettle = false
        settlePinchScale()
    }

    private fun isMatrixAnimating(): Boolean = pinchActive || matrixAnimator?.isRunning == true

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

    private fun settlePinchScale() {
        val start = pinchDisplayScale
        val end = pinchTargetScale.coerceIn(minScale(), maxScale())
        if (abs(start - end) < SCALE_EPSILON) {
            applyPinchDisplayScale(end)
            settleAfterGesture()
            return
        }
        val duration = if (start < minScale() - SCALE_EPSILON) SNAP_DURATION_MS else PINCH_SETTLE_MS
        matrixAnimator?.cancel()
        matrixAnimator = ValueAnimator.ofFloat(start, end).apply {
            this.duration = duration
            interpolator = boundaryInterpolator
            addUpdateListener { anim ->
                applyPinchDisplayScale(anim.animatedValue as Float)
            }
            doOnEnd { settleAfterGesture() }
            start()
        }
    }

    /** 手势结束：动画修正到合法矩阵（缩放 + 贴边）。 */
    private fun settleAfterGesture() {
        if (drawable == null) return
        animateToConstrainedMatrix()
    }

    /**
     * 将当前矩阵平滑过渡到约束后的目标矩阵。
     */
    private fun animateToConstrainedMatrix(
        durationMs: Long = BOUNDARY_SETTLE_MS,
        onEnd: (() -> Unit)? = null
    ) {
        val target = computeConstrainedMatrix(imageMatrixInternal)
        animateMatrixTo(target, durationMs, onEnd)
    }

    /** 在副本上计算约束后的矩阵，不修改当前显示状态。 */
    private fun computeConstrainedMatrix(source: Matrix): Matrix {
        val backup = Matrix(imageMatrixInternal)
        imageMatrixInternal.set(source)
        constrainTranslation(animated = false)
        val result = Matrix(imageMatrixInternal)
        imageMatrixInternal.set(backup)
        return result
    }

    /** 矩阵插值动画。 */
    private fun animateMatrixTo(
        target: Matrix,
        durationMs: Long,
        onEnd: (() -> Unit)? = null
    ) {
        matrixAnimator?.cancel()
        if (matricesNearEqual(imageMatrixInternal, target)) {
            imageMatrixInternal.set(target)
            applyMatrix()
            onEnd?.invoke()
            return
        }
        imageMatrixInternal.getValues(matrixFrom)
        target.getValues(matrixTo)
        matrixAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = boundaryInterpolator
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                for (i in 0..8) {
                    tempValues[i] = matrixFrom[i] + (matrixTo[i] - matrixFrom[i]) * t
                }
                imageMatrixInternal.setValues(tempValues)
                applyMatrix()
            }
            doOnEnd {
                imageMatrixInternal.set(target)
                applyMatrix()
                onEnd?.invoke()
            }
            start()
        }
    }

    private fun matricesNearEqual(a: Matrix, b: Matrix): Boolean {
        a.getValues(matrixFrom)
        b.getValues(matrixTo)
        for (i in 0..8) {
            if (abs(matrixFrom[i] - matrixTo[i]) > MATRIX_EQUAL_EPSILON) return false
        }
        return true
    }

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
            settleAfterGesture()
            return
        }
        zoomStartMatrix.set(imageMatrixInternal)
        cancelMatrixAnimations()
        matrixAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = boundaryInterpolator
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                val scale = startScale + (endScale - startScale) * t
                imageMatrixInternal.set(zoomStartMatrix)
                imageMatrixInternal.postScale(
                    scale / startScale, scale / startScale, pivotX, pivotY
                )
                applyMatrix()
            }
            doOnEnd { settleAfterGesture() }
            start()
        }
    }

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
     * 修正缩放与平移；[animated]=true 时用矩阵动画过渡。
     */
    private fun constrainTranslation(animated: Boolean = false) {
        if (animated) {
            animateToConstrainedMatrix()
            return
        }
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

    private fun clampScaleToBounds() {
        val min = minScale()
        val max = maxScale()
        val current = currentScale()
        val d = drawable ?: return
        when {
            current < min - SCALE_EPSILON -> {
                mapDisplayRect(d)
                val factor = min / current
                imageMatrixInternal.postScale(
                    factor, factor, displayRect.centerX(), displayRect.centerY()
                )
            }
            current > max + SCALE_EPSILON -> {
                mapDisplayRect(d)
                val factor = max / current
                imageMatrixInternal.postScale(
                    factor, factor, displayRect.centerX(), displayRect.centerY()
                )
            }
        }
    }

    private fun constrainAtMinScale(d: Drawable) {
        val min = minScale()
        val current = currentScale()
        if (abs(current - min) > SCALE_EPSILON) {
            mapDisplayRect(d)
            val factor = min / current
            imageMatrixInternal.postScale(
                factor, factor, displayRect.centerX(), displayRect.centerY()
            )
            mapDisplayRect(d)
        }
        val widthFix = cropRect.width() / displayRect.width()
        if (abs(widthFix - 1f) > WIDTH_ALIGN_EPSILON) {
            imageMatrixInternal.postScale(
                widthFix, widthFix, cropRect.centerX(), cropRect.centerY()
            )
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

    private fun constrainFullCover() {
        var dx = 0f
        var dy = 0f
        if (displayRect.left > cropRect.left) dx = cropRect.left - displayRect.left
        if (displayRect.right < cropRect.right) dx = cropRect.right - displayRect.right
        if (displayRect.top > cropRect.top) dy = cropRect.top - displayRect.top
        if (displayRect.bottom < cropRect.bottom) dy = cropRect.bottom - displayRect.bottom
        if (dx != 0f || dy != 0f) {
            imageMatrixInternal.postTranslate(dx, dy)
        }
    }

    private fun constrainBetweenMinAndCover() {
        var dx = 0f
        var dy = 0f
        if (displayRect.width() >= cropRect.width()) {
            if (displayRect.left > cropRect.left) dx = cropRect.left - displayRect.left
            if (displayRect.right < cropRect.right) dx = cropRect.right - displayRect.right
        }
        if (displayRect.height() >= cropRect.height() - 0.5f) {
            if (displayRect.top > cropRect.top) dy = cropRect.top - displayRect.top
            if (displayRect.bottom < cropRect.bottom) dy = cropRect.bottom - displayRect.bottom
        } else {
            dy = cropRect.centerY() - displayRect.centerY()
        }
        if (dx != 0f || dy != 0f) {
            imageMatrixInternal.postTranslate(dx, dy)
        }
    }

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

        val allowPan = event.pointerCount == 1 &&
            !multiTouchActive &&
            !scaleDetector.isInProgress &&
            !pinchActive &&
            !isMatrixAnimating()
        if (allowPan) {
            gestureDetector.onTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (pendingPinchSettle) {
                    settlePinchIfPending()
                } else if (!scaleDetector.isInProgress && !isMatrixAnimating()) {
                    panGestureActive = false
                    settleAfterGesture()
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
     * 输出裁剪结果 Bitmap。
     *
     * @param maxWidth  输出宽度上限（px）；<=0 表示不限制。
     * @param maxHeight 输出高度上限（px）；<=0 表示不限制。
     * 任一维度超限时按最小系数等比收缩，保证输出不超过任一上限；只缩不放（系数封顶 1）。
     */
    fun crop(maxWidth: Int = 0, maxHeight: Int = 0): Bitmap? {
        val sourceBitmap = (drawable as? BitmapDrawable)?.bitmap ?: return null
        if (sourceBitmap.width <= 0 || sourceBitmap.height <= 0) return null

        val cropW = cropRect.width()
        val cropH = cropRect.height()
        if (cropW <= 0f || cropH <= 0f) return null

        // 依据配置的输出尺寸上限等比缩放：任一维度超限即取最小系数收缩，不放大
        var scale = 1f
        if (maxWidth > 0 && cropW > maxWidth) scale = minOf(scale, maxWidth / cropW)
        if (maxHeight > 0 && cropH > maxHeight) scale = minOf(scale, maxHeight / cropH)

        val outW = (cropW * scale).toInt().coerceAtLeast(1)
        val outH = (cropH * scale).toInt().coerceAtLeast(1)

        val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.BLACK)

        val drawMatrix = Matrix(imageMatrixInternal)
        drawMatrix.postTranslate(-cropRect.left, -cropRect.top)
        // 输出尺寸被上限收缩时，绘制矩阵同步等比缩放，使裁剪内容映射到缩小后的画布
        if (scale != 1f) drawMatrix.postScale(scale, scale)
        canvas.drawBitmap(sourceBitmap, drawMatrix, null)
        return result
    }

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
        private const val MATRIX_EQUAL_EPSILON = 0.35f
        private const val PINCH_LERP_FACTOR = 0.38f
        private const val PINCH_SETTLE_MS = 140L
        private const val ZOOM_ANIM_DURATION_MS = 240L
        private const val SNAP_DURATION_MS = 220L
        private const val BOUNDARY_SETTLE_MS = 280L
        private const val ASPECT_RATIO_ANIM_MS = 300L
        private const val PINCH_OVERSHOOT_MIN_RATIO = 0.88f
    }
}
