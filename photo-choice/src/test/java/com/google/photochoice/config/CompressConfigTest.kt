package com.google.photochoice.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressConfigTest {

    @Test
    fun `default matches WeChat-style preset`() {
        val config = CompressConfig(enabled = true)
        assertEquals(1280, config.maxWidth)
        assertEquals(1280, config.maxHeight)
        assertEquals(80, config.quality)
        assertEquals(1_572_864L, config.maxFileSizeBytes)
        assertEquals(50, config.minQuality)
        assertEquals(10, config.qualityStep)
    }

    @Test
    fun `disabled config keeps defaults`() {
        val config = CompressConfig()
        assertFalse(config.enabled)
        assertEquals(CompressConfig.DEFAULT_MAX_EDGE, config.maxWidth)
    }

    @Test
    fun `custom max file size zero disables size iteration semantically`() {
        val config = CompressConfig(enabled = true, maxFileSizeBytes = 0L)
        assertEquals(0L, config.maxFileSizeBytes)
        assertTrue(config.enabled)
    }
}
