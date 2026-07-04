package com.google.photochoice.data.motion

import com.google.photochoice.data.model.MediaFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPhotoHeuristicsTest {

    private fun image(name: String) = MediaFile(
        id = 1L, uri = "content://x/1", mimeType = "image/jpeg",
        type = MediaFile.MediaType.IMAGE, dateAdded = 0L, width = 0, height = 0,
        size = 0L, bucketId = "b", bucketName = "B", displayName = name
    )

    private fun video(name: String) = image(name).copy(type = MediaFile.MediaType.VIDEO)

    @Test
    fun `MVIMG 前缀命中`() {
        assertTrue(MotionPhotoHeuristics.guess(image("MVIMG_20240101_120000.jpg")))
    }

    @Test
    fun `小写 mvimg 也命中(大小写不敏感)`() {
        assertTrue(MotionPhotoHeuristics.guess(image("mvimg_20240101.jpg")))
    }

    @Test
    fun `MV 前缀命中`() {
        assertTrue(MotionPhotoHeuristics.guess(image("MV_1234.jpg")))
    }

    @Test
    fun `包含 MOTIONPHOTO 命中`() {
        assertTrue(MotionPhotoHeuristics.guess(image("IMG_motionphoto_1.jpg")))
    }

    @Test
    fun `普通 IMG 不命中`() {
        assertFalse(MotionPhotoHeuristics.guess(image("IMG_20240101_120000.jpg")))
    }

    @Test
    fun `视频不命中`() {
        assertFalse(MotionPhotoHeuristics.guess(video("MVIMG_1.mp4")))
    }

    @Test
    fun `空文件名不命中`() {
        assertFalse(MotionPhotoHeuristics.guess(image("")))
    }
}
