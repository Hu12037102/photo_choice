package com.google.photochoice.data.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IndexMapMergeTest {

    @Test
    fun `多行合并到 Map`() {
        val map = MotionPhotoIndexStore.mergeLines(
            listOf("1,1,100,10", "2,0,200,20")
        )
        assertEquals(IndexRecord(1L, true, 100L, 10L), map[1L])
        assertEquals(IndexRecord(2L, false, 200L, 20L), map[2L])
    }

    @Test
    fun `同 id 后写覆盖先写`() {
        val map = MotionPhotoIndexStore.mergeLines(
            listOf("1,0,100,10", "1,1,100,10")
        )
        assertEquals(true, map[1L]?.isMotion)
    }

    @Test
    fun `坏行被跳过不影响其它`() {
        val map = MotionPhotoIndexStore.mergeLines(
            listOf("1,1,100,10", "GARBAGE", "2,1,200,20")
        )
        assertEquals(2, map.size)
        assertNull(map[999L])
    }
}
