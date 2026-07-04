package com.google.photochoice.data.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IndexCodecTest {

    @Test
    fun `编码为 CSV 行`() {
        val line = IndexCodec.encode(IndexRecord(id = 42L, isMotion = true, size = 1000L, dateAdded = 123L))
        assertEquals("42,1,1000,123", line)
    }

    @Test
    fun `阴性结果编码 flag 为 0`() {
        val line = IndexCodec.encode(IndexRecord(id = 7L, isMotion = false, size = 5L, dateAdded = 9L))
        assertEquals("7,0,5,9", line)
    }

    @Test
    fun `解码正常行`() {
        val r = IndexCodec.decode("42,1,1000,123")
        assertEquals(IndexRecord(42L, true, 1000L, 123L), r)
    }

    @Test
    fun `编码解码往返一致`() {
        val original = IndexRecord(999L, false, 88L, 77L)
        assertEquals(original, IndexCodec.decode(IndexCodec.encode(original)))
    }

    @Test
    fun `坏行(列数不足)解码返回 null`() {
        assertNull(IndexCodec.decode("42,1"))
    }

    @Test
    fun `坏行(非数字)解码返回 null`() {
        assertNull(IndexCodec.decode("abc,1,2,3"))
    }

    @Test
    fun `空行解码返回 null`() {
        assertNull(IndexCodec.decode(""))
    }
}
