package com.google.photochoice.ui.widget

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
 * 越界回弹 ViewGroup。
 *
 * 任意实现 [NestedScrollingChild][androidx.core.view.NestedScrollingChild] 的子 View
 * （RecyclerView / NestedScrollView / ViewPager2 等）放进来后：
 *   - 滚到顶/底再继续拖，会带着子 View 一起平移；
 *   - 拖得越远阻尼越强（参考微信、iOS 越界手感）；
 *   - 松手或 fling 越界时弹簧回到 0。
 *
 * 仅竖直方向。子 View 必须只有一个直接子节点（多个时只对第一个生效）。
 */
class BounceLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), NestedScrollingParent3 {

    /** 最大可拖动距离 = 自身高度的此比例。手感参考 iOS / 微信，0.6 比较接近。 */
    private val maxOverscrollRatio = 0.6f

    /** 阻尼曲线参数：越大越"硬"，越小越"软"。1.4f 比 2f 更温柔。 */
    private val dampingFactor = 1.4f

    private val parentHelper = NestedScrollingParentHelper(this)

    /** 当前子 View 的偏移量。正数表示子 View 整体向下平移（顶部越界），负数表示向上（底部越界）。 */
    private var currentOffset = 0f

    private var springAnim: ValueAnimator? = null

    /** 手指原始按下点 Y，处理"自身处理 touch"分支用。 */
    private var lastTouchY = 0f
    private var initialDownY = 0f
    private var isSelfDragging = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val targetChild: View?
        get() = if (childCount > 0) getChildAt(0) else null

    // ───────── NestedScrollingParent3 ─────────

    override fun onStartNestedScroll(child: View, target: View, axes: Int, type: Int): Boolean {
        return axes and ViewCompat.SCROLL_AXIS_VERTICAL != 0
    }

    override fun onNestedScrollAccepted(child: View, target: View, axes: Int, type: Int) {
        parentHelper.onNestedScrollAccepted(child, target, axes, type)
        cancelAllAnimations()
    }

    override fun onStopNestedScroll(target: View, type: Int) {
        parentHelper.onStopNestedScroll(target, type)
        if (type == ViewCompat.TYPE_TOUCH && currentOffset != 0f) {
            springBack()
        }
    }

    /**
     * 优先消耗滑动：如果当前有偏移（处于越界拖动态），手指反向滑动时先把偏移消化掉，
     * 再让子 View 接管剩余滑动。
     */
    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
        if (type != ViewCompat.TYPE_TOUCH) return
        if (currentOffset == 0f) return

        // 用户向下滑（dy < 0）时如果当前是底部越界（offset < 0），先回收 offset
        // 用户向上滑（dy > 0）时如果当前是顶部越界（offset > 0），先回收 offset
        if ((currentOffset > 0 && dy > 0) || (currentOffset < 0 && dy < 0)) {
            val newOffset = (currentOffset - dy).let {
                if (currentOffset > 0) it.coerceAtLeast(0f) else it.coerceAtMost(0f)
            }
            val realConsumed = (currentOffset - newOffset).toInt()
            currentOffset = newOffset
            applyOffset(currentOffset)
            consumed[1] = realConsumed
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
        // 只处理 TOUCH 类型；fling 越界不让子 View 跟着走（避免甩动后 view 飞出）
        if (type != ViewCompat.TYPE_TOUCH) return
        if (dyUnconsumed == 0) return

        // dyUnconsumed > 0：滑到底了还在向上滑，offset 应该变负
        // dyUnconsumed < 0：滑到顶了还在向下滑，offset 应该变正
        applyDrag(-dyUnconsumed.toFloat())
        consumed[1] += dyUnconsumed
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
        // 当前有越界偏移时，吃掉 fling，让弹簧动画接管
        return currentOffset != 0f
    }

    override fun getNestedScrollAxes(): Int = parentHelper.nestedScrollAxes

    // ───────── 自处理 touch 兜底（非 NestedScrollingChild 子 View） ─────────

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val child = targetChild ?: return false
        // 若子 View 支持 NestedScrolling，交给它走 NestedScroll 流程；这里不拦截
        if (child.isNestedScrollingEnabled) return super.onInterceptTouchEvent(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialDownY = ev.y
                lastTouchY = ev.y
                isSelfDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = ev.y - initialDownY
                if (!isSelfDragging && abs(dy) > touchSlop) {
                    if (canTakeOverDrag(dy)) {
                        isSelfDragging = true
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
                val delta = ev.y - lastTouchY
                lastTouchY = ev.y
                applyDrag(delta)
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

    /** 自处理 touch 模式下，是否允许接管 drag（仅在子 View 到达边界时）。 */
    private fun canTakeOverDrag(dy: Float): Boolean {
        val child = targetChild ?: return false
        return when {
            dy > 0 -> !child.canScrollVertically(-1)  // 顶部
            dy < 0 -> !child.canScrollVertically(1)   // 底部
            else -> false
        }
    }

    // ───────── 核心：阻尼 + 应用偏移 + 弹回 ─────────

    /**
     * 把"还想拖 delta px"应用到当前偏移上，按阻尼曲线衰减。
     *
     * 公式：offset = sign * H * (1 - 1 / (|drag|/H * factor + 1))
     * 其中 drag 是"未经阻尼的累计拖动量"。
     * 这种曲线在 drag=0 时增益接近 1（手指走多少就动多少），随着 drag 增大快速衰减，
     * 当 drag → ∞ 时 offset → H（永远到不了边界），符合"越拖越费力、永远拖不到底"的手感。
     */
    private fun applyDrag(delta: Float) {
        val h = height.takeIf { it > 0 } ?: return
        // 反推当前等效 drag
        val maxOverscroll = h * maxOverscrollRatio
        val currentDrag = offsetToDrag(currentOffset, maxOverscroll)
        val newDrag = currentDrag + delta
        currentOffset = dragToOffset(newDrag, maxOverscroll)
        applyOffset(currentOffset)
    }

    private fun dragToOffset(drag: Float, maxOverscroll: Float): Float {
        if (drag == 0f) return 0f
        val s = sign(drag)
        val absDrag = abs(drag)
        val o = maxOverscroll * (1f - 1f / (absDrag / maxOverscroll * dampingFactor + 1f))
        return s * o
    }

    /** offset → drag 的逆函数，用于把"已经在越界状态"换算回未阻尼的累计 drag。 */
    private fun offsetToDrag(offset: Float, maxOverscroll: Float): Float {
        if (offset == 0f) return 0f
        val s = sign(offset)
        val absOffset = abs(offset).coerceAtMost(maxOverscroll - 0.01f)
        // 由 o = H(1 - 1/(d/H * k + 1)) 反解：d = (H/(H-o) - 1) * H / k
        val d = (maxOverscroll / (maxOverscroll - absOffset) - 1f) * maxOverscroll / dampingFactor
        return s * d
    }

    private fun applyOffset(offset: Float) {
        targetChild?.translationY = offset
    }

    private fun springBack() {
        if (currentOffset == 0f) return
        // 用 cubic-bezier 曲线 (0.25, 0.1, 0.25, 1) ——CSS 标准 "ease" 曲线，
        // 起手稍快、收尾极慢极柔，无过冲，体感比 Overshoot/弹簧更"温柔平和"。
        val anim = ValueAnimator.ofFloat(currentOffset, 0f).apply {
            duration = SPRING_BACK_DURATION_MS
            interpolator = SPRING_INTERPOLATOR
            addUpdateListener { va ->
                val value = va.animatedValue as Float
                currentOffset = value
                applyOffset(value)
            }
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
        /** 回弹动画时长（ms）。再加长一些让收尾更"飘"。 */
        private const val SPRING_BACK_DURATION_MS = 300L

        /**
         * 回弹曲线：起步柔和、收尾长时间衰减。
         * cubic-bezier(0.25, 0.1, 0.25, 1) —— 即 CSS "ease"，比 Decelerate / Overshoot 更温柔。
         */
        private val SPRING_INTERPOLATOR = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)
    }
}
