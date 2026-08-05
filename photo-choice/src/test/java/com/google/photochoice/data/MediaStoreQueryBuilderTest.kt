package com.google.photochoice.data

import android.provider.MediaStore
import com.google.photochoice.config.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreQueryBuilderTest {

    @Test
    fun `keysetBefore 生成小于游标的条件`() {
        val (selection, args) = MediaStoreQueryBuilder()
            .keysetBefore(afterDateAdded = 1000L, afterId = 5L)
            .build()

        assertEquals(
            "(${MediaStore.Files.FileColumns.DATE_ADDED} < ? OR " +
                "(${MediaStore.Files.FileColumns.DATE_ADDED} = ? AND " +
                "${MediaStore.Files.FileColumns._ID} < ?))",
            selection
        )
        assertEquals(listOf("1000", "1000", "5"), args?.toList())
    }

    @Test
    fun `excludeEmptyFile 排除 0 字节与 SIZE 缺失的行`() {
        val (selection, args) = MediaStoreQueryBuilder()
            .excludeEmptyFile()
            .build()

        assertEquals("${MediaStore.Files.FileColumns.SIZE} > 0", selection)
        // 常量内联，不产生占位参数
        assertTrue(args?.isEmpty() == true)
    }

    @Test
    fun `excludeEmptyFile 与其它条件以 AND 串联`() {
        val (selection, args) = MediaStoreQueryBuilder()
            .mediaType(MediaType.IMAGE)
            .excludePending()
            .excludeEmptyFile()
            .build()

        assertEquals(
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? AND " +
                "${MediaStore.Files.FileColumns.IS_PENDING} = 0 AND " +
                "${MediaStore.Files.FileColumns.SIZE} > 0",
            selection
        )
        assertEquals(
            listOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()),
            args?.toList()
        )
    }

    @Test
    fun `默认 imageSize 不过滤时不拼接体积子句`() {
        val (selection, _) = MediaStoreQueryBuilder()
            .imageSize(MediaType.IMAGE, minImageSizeBytes = 0L, maxImageSizeBytes = Long.MAX_VALUE)
            .excludeEmptyFile()
            .build()

        // 有效性底线（SIZE > 0）生效，宿主可配置的体积策略保持关闭
        assertEquals("${MediaStore.Files.FileColumns.SIZE} > 0", selection)
        assertFalse(selection!!.contains("${MediaStore.Files.FileColumns.SIZE} >= ?"))
    }
}
