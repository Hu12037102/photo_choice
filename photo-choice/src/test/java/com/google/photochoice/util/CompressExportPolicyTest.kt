package com.google.photochoice.util

import com.google.photochoice.data.model.MediaFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressExportPolicyTest {

    @Test
    fun `mimeType 为 image_gif 时判定为 GIF`() {
        val media = sampleImage(mimeType = "image/gif")
        assertTrue(CompressExportPolicy.isGifImage(media))
    }

    @Test
    fun `mimeType 大小写不敏感`() {
        val media = sampleImage(mimeType = "IMAGE/GIF")
        assertTrue(CompressExportPolicy.isGifImage(media))
    }

    @Test
    fun `mimeType 缺失时依据 displayName 扩展名`() {
        val media = sampleImage(mimeType = "", displayName = "anim.GIF")
        assertTrue(CompressExportPolicy.isGifImage(media))
    }

    @Test
    fun `非 GIF 图片返回 false`() {
        val media = sampleImage(mimeType = "image/jpeg", displayName = "photo.jpg")
        assertFalse(CompressExportPolicy.isGifImage(media))
    }

    @Test
    fun `视频类型不判定为 GIF`() {
        val media = sampleImage(mimeType = "image/gif").copy(type = MediaFile.MediaType.VIDEO)
        assertFalse(CompressExportPolicy.isGifImage(media))
    }

    private fun sampleImage(
        mimeType: String,
        displayName: String = "test.gif"
    ) = MediaFile(
        id = 1L,
        uri = "content://media/external/images/media/1",
        mimeType = mimeType,
        type = MediaFile.MediaType.IMAGE,
        dateAdded = 0L,
        width = 100,
        height = 100,
        size = 1024L,
        bucketId = "b",
        bucketName = "Album",
        displayName = displayName
    )
}
