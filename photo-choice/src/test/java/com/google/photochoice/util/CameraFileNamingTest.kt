package com.google.photochoice.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 拍照文件名规则单测。
 *
 * 规则：`IMG` + 时间戳后八位 + 四位随机数 + `.jpg`，固定 19 字符。
 * 该命名是对宿主可见的对外契约（照片落在公共相机目录 DCIM/Camera，用户可直接看到文件名），
 * 故对格式、补零、边界取模行为做锁定。
 */
class CameraFileNamingTest {

    @Test
    fun `名称由 IMG 前缀 八位时间戳 四位随机数与 jpg 扩展名拼成`() {
        val name = CameraHelper.generateDisplayName(
            timestampMillis = 1_754_006_400_123L,
            random = 4821
        )

        assertEquals("IMG064001234821.jpg", name)
    }

    @Test
    fun `时间戳取后八位而非全量`() {
        // 1754006400123 的后八位是 06400123，前面的 17540 必须被丢弃
        val name = CameraHelper.generateDisplayName(timestampMillis = 1_754_006_400_123L, random = 0)

        assertEquals("IMG064001230000.jpg", name)
    }

    @Test
    fun `时间戳后八位不足八位时左补零`() {
        val name = CameraHelper.generateDisplayName(timestampMillis = 42L, random = 7)

        assertEquals("IMG000000420007.jpg", name)
    }

    @Test
    fun `随机数取满四位边界值`() {
        val min = CameraHelper.generateDisplayName(timestampMillis = 0L, random = 0)
        val max = CameraHelper.generateDisplayName(timestampMillis = 0L, random = 9999)

        assertEquals("IMG000000000000.jpg", min)
        assertEquals("IMG000000009999.jpg", max)
    }

    @Test
    fun `随机数越界时取模回落到四位区间而非截断出错`() {
        // 用 mod 而非字符串截取：越界入参不会产生超长或负号文件名
        val name = CameraHelper.generateDisplayName(timestampMillis = 0L, random = 10_007)

        assertEquals("IMG000000000007.jpg", name)
    }

    @Test
    fun `负数入参不产生带负号的非法文件名`() {
        val name = CameraHelper.generateDisplayName(timestampMillis = -1L, random = -1)

        assertTrue("文件名不应含负号：$name", !name.contains('-'))
        assertEquals(NAME_LENGTH, name.length)
    }

    @Test
    fun `默认参数生成的名称长度与格式恒定`() {
        repeat(200) {
            val name = CameraHelper.generateDisplayName()

            assertEquals("长度必须恒定：$name", NAME_LENGTH, name.length)
            assertTrue("必须以 IMG 开头：$name", name.startsWith("IMG"))
            assertTrue("必须以 .jpg 结尾：$name", name.endsWith(".jpg"))
            val digits = name.removePrefix("IMG").removeSuffix(".jpg")
            assertEquals("中间必须是 12 位数字：$name", 12, digits.length)
            assertTrue("中间必须全为数字：$name", digits.all { it.isDigit() })
        }
    }

    private companion object {
        /** "IMG"(3) + 时间戳(8) + 随机数(4) + ".jpg"(4) = 19 */
        const val NAME_LENGTH = 19
    }
}
