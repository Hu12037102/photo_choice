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
    private var updatePosted = false

    private val hideAfterIdleRunnable = Runnable {
        if (recyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
            dateHeader.hide()
        }
    }

    private val updateLabelRunnable = Runnable {
        updatePosted = false
        updateDateLabelNow()
    }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
            when (newState) {
                RecyclerView.SCROLL_STATE_IDLE -> scheduleHideAfterIdle()
                RecyclerView.SCROLL_STATE_DRAGGING,
                RecyclerView.SCROLL_STATE_SETTLING -> {
                    cancelScheduledHide()
                    dateHeader.show()
                    requestDateLabelUpdate()
                }
            }
        }

        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
            if (dy != 0) {
                requestDateLabelUpdate()
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
        cancelPendingUpdate()
        recyclerView.removeOnScrollListener(scrollListener)
        lastLabel = null
        dateHeader.hideImmediately()
    }

    fun reset() {
        cancelScheduledHide()
        cancelPendingUpdate()
        lastLabel = null
        dateHeader.hideImmediately()
    }

    private fun scheduleHideAfterIdle() {
        requestDateLabelUpdate()
        cancelScheduledHide()
        recyclerView.postDelayed(hideAfterIdleRunnable, DesignTokens.DATE_HEADER_IDLE_HOLD_MS)
    }

    private fun cancelScheduledHide() {
        recyclerView.removeCallbacks(hideAfterIdleRunnable)
    }

    /**
     * 快速 fling 时 onScrolled 可能早于本帧布局稳定；延后一帧按当前可见位置取值，
     * 避免日期条显示上一帧或未加载占位 item 的日期。
     */
    private fun requestDateLabelUpdate() {
        if (updatePosted) return
        updatePosted = true
        recyclerView.postOnAnimation(updateLabelRunnable)
    }

    private fun cancelPendingUpdate() {
        updatePosted = false
        recyclerView.removeCallbacks(updateLabelRunnable)
    }

    private fun updateDateLabelNow() {
        val mediaIndex = findTopVisibleMediaIndex() ?: return
        val item = mediaAdapter.mediaAt(mediaIndex) ?: return
        val label = formatter.formatFromDateAddedSeconds(item.dateAdded)
        if (label == lastLabel) return
        lastLabel = label
        dateHeader.setDateLabel(label, crossfade = true)
    }

    private fun findTopVisibleMediaIndex(): Int? {
        val layoutManager = recyclerView.layoutManager as? GridLayoutManager ?: return null
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()
        if (firstVisible == RecyclerView.NO_POSITION || lastVisible == RecyclerView.NO_POSITION) {
            return null
        }

        val start = (firstVisible - leadingItemCount).coerceAtLeast(0)
        val end = (lastVisible - leadingItemCount).coerceAtMost(mediaAdapter.itemCount - 1)
        if (start > end) return null

        for (index in start..end) {
            if (mediaAdapter.mediaAt(index) != null) return index
        }
        return null
    }
}
