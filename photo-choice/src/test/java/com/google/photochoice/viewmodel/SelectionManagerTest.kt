package com.google.photochoice.viewmodel

import com.google.photochoice.config.PhotoChoiceConfig
import com.google.photochoice.config.SelectMode
import com.google.photochoice.data.model.MediaFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionManagerTest {

    @Test
    fun multiSelect_rejectsWhenFull() {
        val manager = SelectionManager(
            PhotoChoiceConfig(maxSelectCount = 2, selectMode = SelectMode.MULTI)
        )
        assertTrue(manager.toggleSelection(file(1L)))
        assertTrue(manager.toggleSelection(file(2L)))
        assertFalse(manager.toggleSelection(file(3L)))
        assertEquals(2, manager.selectionState.value.count)
    }

    @Test
    fun singleSelect_replacesPrevious() {
        val manager = SelectionManager(
            PhotoChoiceConfig(maxSelectCount = 9, selectMode = SelectMode.SINGLE)
        )
        assertTrue(manager.toggleSelection(file(1L)))
        assertTrue(manager.toggleSelection(file(2L)))
        assertEquals(1, manager.selectionState.value.count)
        assertTrue(manager.isSelected(2L))
        assertFalse(manager.isSelected(1L))
    }

    @Test
    fun getSelectionOrder_isOneBased() {
        val manager = SelectionManager(PhotoChoiceConfig())
        manager.toggleSelection(file(10L))
        manager.toggleSelection(file(20L))
        assertEquals(1, manager.getSelectionOrder(10L))
        assertEquals(2, manager.getSelectionOrder(20L))
        assertEquals(-1, manager.getSelectionOrder(99L))
    }

    private fun file(id: Long) = MediaFile(
        id = id,
        uri = "content://media/external/images/media/$id",
        mimeType = "image/jpeg",
        type = MediaFile.MediaType.IMAGE,
        dateAdded = 0L,
        width = 0,
        height = 0,
        size = 0L,
        bucketId = "",
        bucketName = "",
    )
}
