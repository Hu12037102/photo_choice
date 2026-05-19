package com.google.photochoice.ui.crop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.withSave
import com.google.photochoice.R
import com.google.photochoice.config.CropAspectRatio

/**
 * 裁剪视图。
 *
 * 内部维护一个 imageMatrix，使图片初始填满 cropRect，
 * 用户双指缩放 + 单指平移调整图片位置；裁剪框始终居中且固定。
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
    private val cropRect = RectF()
    private val displayRect = RectF()
    private val tempValues = FloatArray(9)

    private val maxScale = 4.0f

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
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                val newScale = (currentScale() * factor).coerceAtMost(maxScale)
                val applied = newScale / currentScale()
                imageMatrixInternal.postScale(applied, applied, detector.focusX, detector.focusY)
                fixTranslation()
                imageMatrix = imageMatrixInternal
                return true
            }
        })

    private val gestureDetector =
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float
            ): Boolean {
                imageMatrixInternal.postTranslate(-dx, -dy)
                fixTranslation()
                imageMatrix = imageMatrixInternal
                return true
            }
        })

    init {
        scaleType = ScaleType.MATRIX
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

    private fun recalcCropRect() {
        val vw = width.toFloat()
        val vh = height.toFloat()
        if (vw <= 0 || vh <= 0) return

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
        } else aspectRatio.ratio ?: 1f

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
    }

    private fun fitImageToCrop() {
        val d = drawable ?: return
        val iw = d.intrinsicWidth.toFloat()
        val ih = d.intrinsicHeight.toFloat()
        if (iw <= 0 || ih <= 0) return

        val cw = cropRect.width()
        val ch = cropRect.height()
        // 让图片完全覆盖 cropRect（短边贴齐）
        val scale = maxOf(cw / iw, ch / ih)
        val tx = cropRect.centerX() - iw * scale / 2f
        val ty = cropRect.centerY() - ih * scale / 2f
        imageMatrixInternal.reset()
        imageMatrixInternal.postScale(scale, scale)
        imageMatrixInternal.postTranslate(tx, ty)
        imageMatrix = imageMatrixInternal
    }

    private fun fixTranslation() {
        val d = drawable ?: return
        displayRect.set(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        imageMatrixInternal.mapRect(displayRect)

        var dx = 0f
        var dy = 0f

        if (displayRect.left > cropRect.left) dx = cropRect.left - displayRect.left
        else if (displayRect.right < cropRect.right) dx = cropRect.right - displayRect.right

        if (displayRect.top > cropRect.top) dy = cropRect.top - displayRect.top
        else if (displayRect.bottom < cropRect.bottom) dy = cropRect.bottom - displayRect.bottom

        if (dx != 0f || dy != 0f) {
            imageMatrixInternal.postTranslate(dx, dy)
        }
    }

    private fun currentScale(): Float {
        imageMatrixInternal.getValues(tempValues)
        val sx = tempValues[Matrix.MSCALE_X]
        val ky = tempValues[Matrix.MSKEW_Y]
        return kotlin.math.sqrt(sx * sx + ky * ky)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (event.pointerCount == 1) gestureDetector.onTouchEvent(event)
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 在 cropRect 外绘制纯色遮罩
        canvas.withSave {
            clipOutRect(cropRect)
            drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint)
        }
        canvas.drawRect(cropRect, borderPaint)
    }

    /**
     * 执行裁剪并返回新的 Bitmap。
     * 通过 imageMatrix 的逆矩阵把 cropRect 映射到原图坐标。
     */
    fun crop(): Bitmap? {
        val sourceBitmap = (drawable as? BitmapDrawable)?.bitmap ?: return null
        if (sourceBitmap.width <= 0 || sourceBitmap.height <= 0) return null

        val inverse = Matrix()
        if (!imageMatrixInternal.invert(inverse)) return null
        val src = RectF(cropRect)
        inverse.mapRect(src)
        src.set(
            src.left.coerceIn(0f, sourceBitmap.width.toFloat()),
            src.top.coerceIn(0f, sourceBitmap.height.toFloat()),
            src.right.coerceIn(0f, sourceBitmap.width.toFloat()),
            src.bottom.coerceIn(0f, sourceBitmap.height.toFloat())
        )
        val w = src.width().toInt()
        val h = src.height().toInt()
        if (w <= 0 || h <= 0) return null
        return Bitmap.createBitmap(
            sourceBitmap,
            src.left.toInt(),
            src.top.toInt(),
            w,
            h
        )
    }
}
