package com.google.photochoice.data.motion

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionPhotoDecisionTest {

    @Test
    fun `L0 系统标记为真 直接确认`() {
        val s = MotionPhotoDecision.resolve(
            isMotionFlag = true, memoryResult = null,
            indexResult = IndexResult.UNKNOWN, heuristicGuess = false
        )
        assertEquals(BadgeState.CONFIRMED_MOTION, s)
    }

    @Test
    fun `L1 内存命中为真 确认`() {
        val s = MotionPhotoDecision.resolve(
            isMotionFlag = false, memoryResult = true,
            indexResult = IndexResult.UNKNOWN, heuristicGuess = true
        )
        assertEquals(BadgeState.CONFIRMED_MOTION, s)
    }

    @Test
    fun `L1 内存命中为假 确认否(优先级高于启发式)`() {
        val s = MotionPhotoDecision.resolve(
            isMotionFlag = false, memoryResult = false,
            indexResult = IndexResult.UNKNOWN, heuristicGuess = true
        )
        assertEquals(BadgeState.CONFIRMED_NOT, s)
    }

    @Test
    fun `L2 索引命中为真 确认`() {
        val s = MotionPhotoDecision.resolve(
            isMotionFlag = false, memoryResult = null,
            indexResult = IndexResult.MOTION, heuristicGuess = false
        )
        assertEquals(BadgeState.CONFIRMED_MOTION, s)
    }

    @Test
    fun `L2 索引命中为假 确认否`() {
        val s = MotionPhotoDecision.resolve(
            isMotionFlag = false, memoryResult = null,
            indexResult = IndexResult.NOT_MOTION, heuristicGuess = true
        )
        assertEquals(BadgeState.CONFIRMED_NOT, s)
    }

    @Test
    fun `L3 启发式命中(前面均未知) 疑似`() {
        val s = MotionPhotoDecision.resolve(
            isMotionFlag = false, memoryResult = null,
            indexResult = IndexResult.UNKNOWN, heuristicGuess = true
        )
        assertEquals(BadgeState.HEURISTIC_MOTION, s)
    }

    @Test
    fun `L4 全未知且启发式不命中 未知`() {
        val s = MotionPhotoDecision.resolve(
            isMotionFlag = false, memoryResult = null,
            indexResult = IndexResult.UNKNOWN, heuristicGuess = false
        )
        assertEquals(BadgeState.UNKNOWN, s)
    }
}
