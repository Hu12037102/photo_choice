package com.google.photochoice.ui.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.core.view.NestedScrollingParent3
import androidx.core.view.NestedScrollingParentHelper
import androidx.core.view.ViewCompat
import kotlin.math.abs
import kotlin.math.sign

/**
 * 越界回弹 ViewGroup，支持竖直与水平方向。
 *
 * 任意实现 [NestedScrollingChild][androidx.core.view.NestedScrollingChild] 的子 View
 * （RecyclerView / NestedScrollView / ViewPager2 等）放进来后：
 *   - 滚到边界再继续拖，会带着子 View 一起平移；
 *   - 拖得越远阻尼越强；
 *   - 松手或 fling 越界时弹簧回到 0。
 *
 * 子 View 必须只有一个直接子节点（多个时只对第一个生效）。
 */
class BounceLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), NestedScrollingParent3 {

    /** 最大可拖动距离 = 自身宽/高的此比例。 */
    private val maxOverscrollRatio = 0.6f

    /** 阻尼曲线参数：越大越"硬"，越小越"软"。 */
    private val dampingFactor = 1.4f

    private val parentHelper = NestedScrollingParentHelper(this)

    private var currentOffsetX = 0f
    private var currentOffsetY = 0f

    private var springAnim: ValueAnimator? = null

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var initialDownX = 0f
    private var initialDownY = 0f
    private var isSelfDragging = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val targetChild: View?
        get() = if (childCount > 0) getChildAt(0) else null

    // ───────── NestedScrollingParent3 ─────────

    override fun onStartNestedScroll(child: View, target: View, axes: Int, type: Int): Boolean {
        return axes and (ViewCompat.SCROLL_AXIS_VERTICAL or ViewCompat.SCROLL_AXIS_HORIZONTAL) != 0
    }

    override fun onNestedScrollAccepted(child: View, target: View, axes: Int, type: Int) {
        parentHelper.onNestedScrollAccepted(child, target, axes, type)
        cancelAllAnimations()
    }

    override fun onStopNestedScroll(target: View, type: Int) {
        parentHelper.onStopNestedScroll(target, type)
        if (type == ViewCompat.TYPE_TOUCH && (currentOffsetX != 0f || currentOffsetY != 0f)) {
            springBack()
        }
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
        if (type != ViewCompat.TYPE_TOUCH) return

        if (currentOffsetX != 0f) {
            if ((currentOffsetX > 0 && dx > 0) || (currentOffsetX < 0 && dx < 0)) {
                val newOffset = (currentOffsetX - dx).let {
                    if (currentOffsetX > 0) it.coerceAtLeast(0f) else it.coerceAtMost(0f)
                }
                val realConsumed = (currentOffsetX - newOffset).toInt()
                currentOffsetX = newOffset
                applyOffset()
                consumed[0] = realConsumed
            }
        }

        if (currentOffsetY != 0f) {
            if ((currentOffsetY > 0 && dy > 0) || (currentOffsetY < 0 && dy < 0)) {
                val newOffset = (currentOffsetY - dy).let {
                    if (currentOffsetY > 0) it.coerceAtLeast(0f) else it.coerceAtMost(0f)
                }
                val realConsumed = (currentOffsetY - newOffset).toInt()
                currentOffsetY = newOffset
                applyOffset()
                consumed[1] = realConsumed
            }
        }
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray
    ) {
        if (type != ViewCompat.TYPE_TOUCH) return

        if (dxUnconsumed != 0) {
            applyDragX(-dxUnconsumed.toFloat())
            consumed[0] += dxUnconsumed
        }
        if (dyUnconsumed != 0) {
            applyDragY(-dyUnconsumed.toFloat())
            consumed[1] += dyUnconsumed
        }
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int
    ) {
        onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, intArrayOf(0, 0))
    }

    override fun onNestedPreFling(target: View, velocityX: Float, velocityY: Float): Boolean {
        return currentOffsetX != 0f || currentOffsetY != 0f
    }

    override fun getNestedScrollAxes(): Int = parentHelper.nestedScrollAxes

    // ───────── 自处理 touch 兜底（非 NestedScrollingChild 子 View） ─────────

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val child = targetChild ?: return false
        if (child.isNestedScrollingEnabled) return super.onInterceptTouchEvent(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialDownX = ev.x
                initialDownY = ev.y
                lastTouchX = ev.x
                lastTouchY = ev.y
                isSelfDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - initialDownX
                val dy = ev.y - initialDownY
                if (!isSelfDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    if (canTakeOverDrag(dx, dy)) {
                        isSelfDragging = true
                        lastTouchX = ev.x
                        lastTouchY = ev.y
                        return true
                    }
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!isSelfDragging) return super.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val deltaX = ev.x - lastTouchX
                val deltaY = ev.y - lastTouchY
                lastTouchX = ev.x
                lastTouchY = ev.y
                applyDragX(deltaX)
                applyDragY(deltaY)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isSelfDragging = false
                springBack()
                return true
            }
        }
        return super.onTouchEvent(ev)
    }

    /** 自处理 touch 模式下，是否允许接管 drag（仅在子 View 到达边界且沿主导轴拖拽时）。 */
    private fun canTakeOverDrag(dx: Float, dy: Float): Boolean {
        val child = targetChild ?: return false
        val absDx = abs(dx)
        val absDy = abs(dy)
        return when {
            absDy > absDx && dy > 0 -> !child.canScrollVertically(-1)
            absDy > absDx && dy < 0 -> !child.canScrollVertically(1)
            absDx > absDy && dx > 0 -> !child.canScrollHorizontally(-1)
            absDx > absDy && dx < 0 -> !child.canScrollHorizontally(1)
            else -> false
        }
    }

    // ───────── 核心：阻尼 + 应用偏移 + 弹回 ─────────

    private fun applyDragX(delta: Float) {
        val w = width.takeIf { it > 0 } ?: return
        val maxOverscroll = w * maxOverscrollRatio
        val currentDrag = offsetToDrag(currentOffsetX, maxOverscroll)
        val newDrag = currentDrag + delta
        currentOffsetX = dragToOffset(newDrag, maxOverscroll)
        applyOffset()
    }

    private fun applyDragY(delta: Float) {
        val h = height.takeIf { it > 0 } ?: return
        val maxOverscroll = h * maxOverscrollRatio
        val currentDrag = offsetToDrag(currentOffsetY, maxOverscroll)
        val newDrag = currentDrag + delta
        currentOffsetY = dragToOffset(newDrag, maxOverscroll)
        applyOffset()
    }

    /**
     * 阻尼公式：offset = sign * H * (1 - 1 / (|drag|/H * factor + 1))
     * 其中 H 为对应方向的最大越界距离（width * ratio 或 height * ratio）。
     */
    private fun dragToOffset(drag: Float, maxOverscroll: Float): Float {
        if (drag == 0f) return 0f
        val s = sign(drag)
        val absDrag = abs(drag)
        val o = maxOverscroll * (1f - 1f / (absDrag / maxOverscroll * dampingFactor + 1f))
        return s * o
    }

    /** offset → drag 的逆函数。 */
    private fun offsetToDrag(offset: Float, maxOverscroll: Float): Float {
        if (offset == 0f) return 0f
        val s = sign(offset)
        val absOffset = abs(offset).coerceAtMost(maxOverscroll - 0.01f)
        val d = (maxOverscroll / (maxOverscroll - absOffset) - 1f) * maxOverscroll / dampingFactor
        return s * d
    }

    private fun applyOffset() {
        targetChild?.translationX = currentOffsetX
        targetChild?.translationY = currentOffsetY
    }

    private fun springBack() {
        if (currentOffsetX == 0f && currentOffsetY == 0f) return
        val startX = currentOffsetX
        val startY = currentOffsetY
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SPRING_BACK_DURATION_MS
            interpolator = SPRING_INTERPOLATOR
            addUpdateListener { va ->
                val fraction = va.animatedValue as Float
                currentOffsetX = startX * (1f - fraction)
                currentOffsetY = startY * (1f - fraction)
                applyOffset()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentOffsetX = 0f
                    currentOffsetY = 0f
                    applyOffset()
                }
            })
        }
        springAnim?.cancel()
        springAnim = anim
        anim.start()
    }

    private fun cancelAllAnimations() {
        springAnim?.cancel()
        springAnim = null
    }

    override fun onDetachedFromWindow() {
        cancelAllAnimations()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val SPRING_BACK_DURATION_MS = 300L

        /** cubic-bezier(0.25, 0.1, 0.25, 1) —— CSS "ease"，起手柔和、收尾长时间衰减。 */
        private val SPRING_INTERPOLATOR = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)
    }
}
