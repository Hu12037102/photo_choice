package com.google.photochoice.data

import android.provider.MediaStore
import org.junit.Assert.assertEquals
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
}
