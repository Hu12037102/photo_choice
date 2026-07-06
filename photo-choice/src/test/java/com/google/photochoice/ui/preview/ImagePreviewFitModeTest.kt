package com.google.photochoice.ui.preview

import org.junit.Assert.assertEquals
import org.junit.Test

class ImagePreviewFitModeTest {

    @Test
    fun `正常横图按宽高都不溢出时使用居中自适应`() {
        // 1080x1920 屏幕，1200x900 横图：按宽适配(0.9)先于按高适配(2.133)触顶
        assertEquals(
            ImagePreviewFitMode.CENTER,
            resolveImagePreviewFitMode(
                viewWidth = 1080f, viewHeight = 1920f,
                imageWidth = 1200f, imageHeight = 900f
            )
        )
    }

    @Test
    fun `正常竖拍照片按宽适配即可完整显示时使用居中自适应`() {
        // 1080x1920 屏幕，1200x1600 竖拍照片：按宽适配(0.9)先于按高适配(1.2)触顶
        assertEquals(
            ImagePreviewFitMode.CENTER,
            resolveImagePreviewFitMode(
                viewWidth = 1080f, viewHeight = 1920f,
                imageWidth = 1200f, imageHeight = 1600f
            )
        )
    }

    @Test
    fun `明显偏窄的竖图按高适配后宽度占比不足阈值时使用居中自适应`() {
        // 400x3000 窄长条图：按高适配后显示宽度 256px，小于屏宽 40%(432px)
        assertEquals(
            ImagePreviewFitMode.CENTER,
            resolveImagePreviewFitMode(
                viewWidth = 1080f, viewHeight = 1920f,
                imageWidth = 400f, imageHeight = 3000f
            )
        )
    }

    @Test
    fun `长图按高适配后宽度占比达到阈值时整宽顶对齐`() {
        // 1080x3000 长图：按高适配后显示宽度 691.2px，大于屏宽 40%(432px)
        assertEquals(
            ImagePreviewFitMode.FIT_WIDTH_TOP_ALIGNED,
            resolveImagePreviewFitMode(
                viewWidth = 1080f, viewHeight = 1920f,
                imageWidth = 1080f, imageHeight = 3000f
            )
        )
    }

    @Test
    fun `按高适配后宽度恰好等于阈值时判定为居中自适应`() {
        // 1000x2000 屏幕，800x4000 图：按高适配后显示宽度恰好等于屏宽的 40%(400px)，
        // 规则是“大于阈值才整宽”，恰好等于时仍按居中自适应处理
        assertEquals(
            ImagePreviewFitMode.CENTER,
            resolveImagePreviewFitMode(
                viewWidth = 1000f, viewHeight = 2000f,
                imageWidth = 800f, imageHeight = 4000f
            )
        )
    }

    @Test
    fun `按高适配后宽度刚超过阈值时判定为长图`() {
        // 1000x2000 屏幕，801x4000 图：按高适配后显示宽度 400.5px，刚超过屏宽的 40%(400px)
        assertEquals(
            ImagePreviewFitMode.FIT_WIDTH_TOP_ALIGNED,
            resolveImagePreviewFitMode(
                viewWidth = 1000f, viewHeight = 2000f,
                imageWidth = 801f, imageHeight = 4000f
            )
        )
    }

    @Test
    fun `正方形图片使用居中自适应`() {
        assertEquals(
            ImagePreviewFitMode.CENTER,
            resolveImagePreviewFitMode(
                viewWidth = 1080f, viewHeight = 1920f,
                imageWidth = 1000f, imageHeight = 1000f
            )
        )
    }
}
