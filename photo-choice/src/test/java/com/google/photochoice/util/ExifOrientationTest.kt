package com.google.photochoice.util

import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ExifOrientation] 的方向映射测试。
 *
 * 重点覆盖 4 种含镜像的方向——它们是历史上被漏掉的部分，漏掉会让输出图左右颠倒。
 */
class ExifOrientationTest {

    @Test
    fun `NORMAL 为恒等变换`() {
        val transform = ExifOrientation.transformOf(ExifInterface.ORIENTATION_NORMAL)
        assertTrue(transform.isIdentity)
    }

    @Test
    fun `三种纯旋转方向不镜像`() {
        val rotate90 = ExifOrientation.transformOf(ExifInterface.ORIENTATION_ROTATE_90)
        assertEquals(90f, rotate90.rotationDegrees, 0f)
        assertFalse(rotate90.flipHorizontal)

        val rotate180 = ExifOrientation.transformOf(ExifInterface.ORIENTATION_ROTATE_180)
        assertEquals(180f, rotate180.rotationDegrees, 0f)
        assertFalse(rotate180.flipHorizontal)

        val rotate270 = ExifOrientation.transformOf(ExifInterface.ORIENTATION_ROTATE_270)
        assertEquals(-90f, rotate270.rotationDegrees, 0f)
        assertFalse(rotate270.flipHorizontal)
    }

    @Test
    fun `四种镜像方向都置位 flipHorizontal`() {
        val mirrored = listOf(
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
            ExifInterface.ORIENTATION_FLIP_VERTICAL,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_TRANSVERSE
        )
        mirrored.forEach { orientation ->
            assertTrue(
                "orientation=$orientation 应当镜像",
                ExifOrientation.transformOf(orientation).flipHorizontal
            )
        }
    }

    @Test
    fun `TRANSPOSE 为旋转 90 度加镜像`() {
        val transform = ExifOrientation.transformOf(ExifInterface.ORIENTATION_TRANSPOSE)
        assertEquals(90f, transform.rotationDegrees, 0f)
        assertTrue(transform.flipHorizontal)
        assertFalse(transform.isIdentity)
    }

    @Test
    fun `TRANSVERSE 为旋转负 90 度加镜像`() {
        val transform = ExifOrientation.transformOf(ExifInterface.ORIENTATION_TRANSVERSE)
        assertEquals(-90f, transform.rotationDegrees, 0f)
        assertTrue(transform.flipHorizontal)
    }

    @Test
    fun `FLIP_HORIZONTAL 只镜像不旋转`() {
        val transform = ExifOrientation.transformOf(ExifInterface.ORIENTATION_FLIP_HORIZONTAL)
        assertEquals(0f, transform.rotationDegrees, 0f)
        assertTrue(transform.flipHorizontal)
    }

    @Test
    fun `FLIP_VERTICAL 等价于旋转 180 度后镜像`() {
        val transform = ExifOrientation.transformOf(ExifInterface.ORIENTATION_FLIP_VERTICAL)
        assertEquals(180f, transform.rotationDegrees, 0f)
        assertTrue(transform.flipHorizontal)
    }

    @Test
    fun `UNDEFINED 与非法取值退化为恒等变换`() {
        assertTrue(
            ExifOrientation.transformOf(ExifInterface.ORIENTATION_UNDEFINED).isIdentity
        )
        // 越界脏数据不能导致图片被错误旋转
        assertTrue(ExifOrientation.transformOf(0).isIdentity)
        assertTrue(ExifOrientation.transformOf(99).isIdentity)
        assertTrue(ExifOrientation.transformOf(-1).isIdentity)
    }
}
