package com.google.photochoice.ui.grid

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * 网格间距：保证左右与上下完全等距。
 *
 * 实现思路：单元格被均分，使每个 item 的左右总额 = spacing。
 * - 列 i: left = i * spacing / span, right = spacing - (i+1) * spacing / span
 * - 行 (除首行): top = spacing
 */
class GridSpacingItemDecoration(
    private val spacingPx: Int,
    private val includeEdge: Boolean = true
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val lp = view.layoutParams as? GridLayoutManager.LayoutParams ?: return
        val lm = parent.layoutManager as? GridLayoutManager ?: return
        val spanCount = lm.spanCount
        val spanSize = lp.spanSize
        val spanIndex = lp.spanIndex
        if (spanIndex == GridLayoutManager.LayoutParams.INVALID_SPAN_ID) return

        // 跨越整行的 item（如 header）不需要左右间距
        if (spanSize == spanCount) {
            outRect.left = 0
            outRect.right = 0
            outRect.top = if (includeEdge) spacingPx else 0
            outRect.bottom = 0
            return
        }

        if (includeEdge) {
            outRect.left = spacingPx - spanIndex * spacingPx / spanCount
            outRect.right = (spanIndex + spanSize) * spacingPx / spanCount
            outRect.top = spacingPx
            outRect.bottom = 0
        } else {
            outRect.left = spanIndex * spacingPx / spanCount
            outRect.right = spacingPx - (spanIndex + spanSize) * spacingPx / spanCount
            outRect.top = if (parent.getChildAdapterPosition(view) >= spanCount) spacingPx else 0
            outRect.bottom = 0
        }
    }
}
