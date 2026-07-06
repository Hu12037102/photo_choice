package com.google.photochoice.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PhotoChoiceConfig] 防御性规整逻辑的单元测试：
 * 图片体积过滤区间的交换 / 负值回退 / 生效判定。
 */
class PhotoChoiceConfigTest {

    @Test
    fun `default image size range means no filter`() {
        val config = PhotoChoiceConfig()
        assertEquals(0L, config.sanitizedMinImageSize)
        assertEquals(Long.MAX_VALUE, config.sanitizedMaxImageSize)
        assertFalse(config.hasImageSizeFilter)
    }

    @Test
    fun `min greater than max swaps automatically`() {
        val config = PhotoChoiceConfig(minImageSize = 5_000L, maxImageSize = 1_000L)
        assertEquals(1_000L, config.sanitizedMinImageSize)
        assertEquals(5_000L, config.sanitizedMaxImageSize)
        assertTrue(config.hasImageSizeFilter)
    }

    @Test
    fun `negative min falls back to zero`() {
        val config = PhotoChoiceConfig(minImageSize = -1L)
        assertEquals(0L, config.sanitizedMinImageSize)
        assertFalse(config.hasImageSizeFilter)
    }

    @Test
    fun `min only filter is effective`() {
        val config = PhotoChoiceConfig(minImageSize = 10_240L)
        assertEquals(10_240L, config.sanitizedMinImageSize)
        assertEquals(Long.MAX_VALUE, config.sanitizedMaxImageSize)
        assertTrue(config.hasImageSizeFilter)
    }

    @Test
    fun `max only filter is effective`() {
        val config = PhotoChoiceConfig(maxImageSize = 10L * 1024 * 1024)
        assertEquals(0L, config.sanitizedMinImageSize)
        assertEquals(10L * 1024 * 1024, config.sanitizedMaxImageSize)
        assertTrue(config.hasImageSizeFilter)
    }
}
