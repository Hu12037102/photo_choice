package com.google.photochoice.ui.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverscrollDampingTest {

    @Test
    fun `未越界时增量原样通过`() {
        assertEquals(20f, dampOverscrollDelta(delta = 20f, overshoot = 0f, maxOvershoot = 100f), 0.001f)
    }

    @Test
    fun `越界量达到上限时增量完全衰减为0`() {
        assertEquals(0f, dampOverscrollDelta(delta = 20f, overshoot = 100f, maxOvershoot = 100f), 0.001f)
    }

    @Test
    fun `越界量为上限一半时增量衰减一半`() {
        assertEquals(10f, dampOverscrollDelta(delta = 20f, overshoot = 50f, maxOvershoot = 100f), 0.001f)
    }

    @Test
    fun `越界量超过上限时不反向仍完全衰减为0`() {
        assertEquals(0f, dampOverscrollDelta(delta = 20f, overshoot = 150f, maxOvershoot = 100f), 0.001f)
    }

    @Test
    fun `未设置越界上限时不阻尼`() {
        assertEquals(20f, dampOverscrollDelta(delta = 20f, overshoot = 50f, maxOvershoot = 0f), 0.001f)
    }

    @Test
    fun `持续拖拽时越界量会随拖拽距离增长而非收敛到极小定值`() {
        // 复现并锁定修复效果：旧实现每帧都对"已越界位移"重新打 25% 折扣再拉回，
        // 会在几帧内收敛到一个和继续拖拽距离无关的极小定值，表现为"拖不动"。
        // 新模型下，只要越界量还没接近上限，持续拖拽应让越界量持续明显增长。
        val maxOvershoot = 100f
        var overshoot = 0f
        repeat(20) {
            val damped = dampOverscrollDelta(delta = 10f, overshoot = overshoot, maxOvershoot = maxOvershoot)
            overshoot += damped
        }
        assertTrue("越界量应持续增长而非卡在极小值，实际=$overshoot", overshoot > 50f)
    }
}
