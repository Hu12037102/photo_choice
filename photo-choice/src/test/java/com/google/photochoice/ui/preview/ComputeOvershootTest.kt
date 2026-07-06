package com.google.photochoice.ui.preview

import org.junit.Assert.assertEquals
import org.junit.Test

class ComputeOvershootTest {

    @Test
    fun `内容未超出可视区域时不算越界`() {
        // contentEnd-contentStart(50) <= viewSize(100)
        assertEquals(0f, computeOvershoot(contentStart = 0f, contentEnd = 50f, viewSize = 100f, delta = 10f), 0.001f)
    }

    @Test
    fun `向下或向右拖拽时仍在合法范围内不算越界`() {
        // 长图/放大图片正常向下拖拽（还原到起点方向）：contentStart<=0，还有内容可看
        assertEquals(
            0f,
            computeOvershoot(contentStart = -500f, contentEnd = 400f, viewSize = 300f, delta = 10f),
            0.001f
        )
    }

    @Test
    fun `向下或向右拖拽越过起始边界时返回越界量`() {
        assertEquals(
            30f,
            computeOvershoot(contentStart = 30f, contentEnd = 730f, viewSize = 300f, delta = 10f),
            0.001f
        )
    }

    @Test
    fun `向上或向左拖拽滚动长图正文时不应算作越界`() {
        // 这是本次要修复的核心场景：长图从顶部向上滑动查看后续内容时，内容底部
        // (contentEnd) 还远远没有到达可视区域(viewSize)，属于合法滚动，不是越界，
        // 阻尼不应介入，否则长图会表现为"划不动"。
        assertEquals(
            0f,
            computeOvershoot(contentStart = 0f, contentEnd = 3000f, viewSize = 1920f, delta = -10f),
            0.001f
        )
    }

    @Test
    fun `向上或向左拖拽越过终止边界时返回越界量`() {
        // 内容底部已经被拖到可视区域上方，产生下方空白，属于真正越界
        assertEquals(
            50f,
            computeOvershoot(contentStart = -1130f, contentEnd = 1870f, viewSize = 1920f, delta = -10f),
            0.001f
        )
    }

    @Test
    fun `增量为0时不算越界`() {
        assertEquals(
            0f,
            computeOvershoot(contentStart = 30f, contentEnd = 730f, viewSize = 300f, delta = 0f),
            0.001f
        )
    }
}
