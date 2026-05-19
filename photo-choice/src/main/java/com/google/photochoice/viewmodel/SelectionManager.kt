package com.google.photochoice.viewmodel

import com.google.photochoice.config.PhotoChoiceConfig
import com.google.photochoice.config.SelectMode
import com.google.photochoice.data.model.MediaFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 选中状态快照，供 UI 层 collect。
 */
data class SelectionState(
    val items: List<MediaFile> = emptyList(),
    val ids: Set<Long> = emptySet(),
    val count: Int = 0,
    val isFull: Boolean = false,
    val canConfirm: Boolean = false
) {
    val orderedIds: List<Long> get() = items.map { it.id }
}

/**
 * 选中管理器：维护已选中的 MediaFile 集合，强制数量限制与类型过滤。
 *
 * - 使用 LinkedHashMap 保持插入顺序
 * - SINGLE 模式：新选会替换旧选
 * - MULTI 模式：达到 maxSelectCount 时新选被拒绝
 */
class SelectionManager(private val config: PhotoChoiceConfig) {

    private val selected = LinkedHashMap<Long, MediaFile>()
    private val _selectionState = MutableStateFlow(emit())

    val selectionState: StateFlow<SelectionState> = _selectionState.asStateFlow()

    /**
     * 切换选中状态。
     *
     * @return true 表示状态发生改变；false 表示请求被拒绝（达到上限）。
     */
    fun toggleSelection(mediaFile: MediaFile): Boolean {
        return if (selected.containsKey(mediaFile.id)) {
            selected.remove(mediaFile.id)
            publish()
            true
        } else {
            if (config.selectMode == SelectMode.SINGLE) {
                selected.clear()
                selected[mediaFile.id] = mediaFile
                publish()
                return true
            }
            if (selected.size >= config.maxSelectCount) {
                return false
            }
            selected[mediaFile.id] = mediaFile
            publish()
            true
        }
    }

    /** 选中一项（已经选中则不变）。 */
    fun select(mediaFile: MediaFile): Boolean {
        if (selected.containsKey(mediaFile.id)) return true
        if (config.selectMode == SelectMode.SINGLE) {
            selected.clear()
            selected[mediaFile.id] = mediaFile
            publish()
            return true
        }
        if (selected.size >= config.maxSelectCount) return false
        selected[mediaFile.id] = mediaFile
        publish()
        return true
    }

    /** 按 ID 取消选中。 */
    fun deselectById(id: Long) {
        if (selected.remove(id) != null) publish()
    }

    fun isSelected(id: Long): Boolean = selected.containsKey(id)

    /** 1-based 选中序号；未选中返回 -1。 */
    fun getSelectionOrder(id: Long): Int {
        if (!selected.containsKey(id)) return -1
        var i = 0
        for (key in selected.keys) {
            i++
            if (key == id) return i
        }
        return -1
    }

    fun getSelectedIds(): List<Long> = selected.keys.toList()

    fun getSelectedItems(): List<MediaFile> = selected.values.toList()

    fun clearAll() {
        if (selected.isEmpty()) return
        selected.clear()
        publish()
    }

    private fun publish() {
        _selectionState.value = emit()
    }

    private fun emit(): SelectionState {
        val items = selected.values.toList()
        return SelectionState(
            items = items,
            ids = selected.keys.toSet(),
            count = items.size,
            isFull = items.size >= config.maxSelectCount,
            canConfirm = items.size >= config.minSelectCount
        )
    }
}
