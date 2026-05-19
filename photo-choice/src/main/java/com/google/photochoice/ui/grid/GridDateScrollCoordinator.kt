package com.google.photochoice.ui.grid

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.photochoice.config.DesignTokens
import com.google.photochoice.ui.widget.ScrollingDateHeaderBar

/**
 * 根据网格滚动位置更新 [ScrollingDateHeaderBar] 的日期文案与显隐。
 *
 * 滚动中显示日期条；手指离开且列表静止后，再保留 [DesignTokens.DATE_HEADER_IDLE_HOLD_MS] 后隐藏。
 */
class GridDateScrollCoordinator(
    private val recyclerView: RecyclerView,
    private val mediaAdapter: MediaGridAdapter,
    private val leadingItemCount: Int,
    private val dateHeader: ScrollingDateHeaderBar,
    private val formatter: DateLabelFormatter,
) {

    private var attached = false
    private var lastLabel: String? = null

    private val hideAfterIdleRunnable = Runnable {
        if (recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
            dateHeader.hide()
        }
    }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
            when (newState) {
                RecyclerView.SCROLL_STATE_IDLE -> scheduleHideAfterIdle()
                RecyclerView.SCROLL_STATE_DRAGGING,
                RecyclerView.SCROLL_STATE_SETTLING -> {
                    cancelScheduledHide()
                    dateHeader.show()
                    updateDateLabel()
                }
            }
        }

        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
            if (dy != 0) {
                updateDateLabel()
            }
        }
    }

    fun attach() {
        if (attached) return
        attached = true
        recyclerView.addOnScrollListener(scrollListener)
    }

    fun detach() {
        if (!attached) return
        attached = false
        cancelScheduledHide()
        recyclerView.removeOnScrollListener(scrollListener)
        lastLabel = null
        dateHeader.hideImmediately()
    }

    fun reset() {
        cancelScheduledHide()
        lastLabel = null
        dateHeader.hideImmediately()
    }

    private fun scheduleHideAfterIdle() {
        updateDateLabel()
        cancelScheduledHide()
        recyclerView.postDelayed(hideAfterIdleRunnable, DesignTokens.DATE_HEADER_IDLE_HOLD_MS)
    }

    private fun cancelScheduledHide() {
        recyclerView.removeCallbacks(hideAfterIdleRunnable)
    }

    private fun updateDateLabel() {
        val mediaIndex = findTopVisibleMediaIndex() ?: return
        val item = mediaAdapter.snapshot().items.getOrNull(mediaIndex) ?: return
        val label = formatter.formatFromDateAddedSeconds(item.dateAdded)
        if (label == lastLabel) return
        lastLabel = label
        dateHeader.setDateLabel(label, crossfade = true)
    }

    private fun findTopVisibleMediaIndex(): Int? {
        val layoutManager = recyclerView.layoutManager as? GridLayoutManager ?: return null
        var bestIndex: Int? = null
        var minTop = Int.MAX_VALUE
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            val position = recyclerView.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) continue
            val mediaIndex = position - leadingItemCount
            if (mediaIndex < 0) continue
            val top = child.top - recyclerView.paddingTop
            if (top < minTop) {
                minTop = top
                bestIndex = mediaIndex
            }
        }
        if (bestIndex != null) return bestIndex

        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        if (firstVisible == RecyclerView.NO_POSITION) return null
        val index = firstVisible - leadingItemCount
        return index.coerceAtLeast(0)
    }
}
