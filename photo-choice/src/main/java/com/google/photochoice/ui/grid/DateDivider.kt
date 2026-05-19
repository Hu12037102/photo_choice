package com.google.photochoice.ui.grid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.photochoice.R
import com.google.photochoice.util.dp
import com.google.photochoice.util.dpF
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 在网格中按日期分组显示 Header（今天 / 昨天 / X 月 X 日）。
 *
 * [leadingItemCount] 为 ConcatAdapter 中位于媒体列表之前的 item 数（如相机格 = 1）。
 */
class DateDivider(
    context: Context,
    private val mediaAdapter: MediaGridAdapter,
    private val leadingItemCount: Int,
    @Suppress("unused") private val spanCount: Int
) : RecyclerView.ItemDecoration() {

    private val headerHeightPx = context.dp(36)
    private val headerPaddingTopPx = context.dp(12)
    private val headerPaddingStartPx = context.dp(16)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.photochoice_date_text)
        textSize = context.dpF(12f)
    }
    private val today by lazy { startOfToday() }
    private val yesterday by lazy { today - DAY_MS }
    private val dayFormat = SimpleDateFormat("M月d日", Locale.getDefault())
    private val yearFormat = SimpleDateFormat("yyyy年M月d日", Locale.getDefault())
    private val todayLabel = context.getString(R.string.photochoice_today)
    private val yesterdayLabel = context.getString(R.string.photochoice_yesterday)
    private val thisYear by lazy {
        Calendar.getInstance().get(Calendar.YEAR)
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val pos = parent.getChildAdapterPosition(view)
        if (pos == RecyclerView.NO_POSITION) return
        val mediaIndex = pos - leadingItemCount
        if (mediaIndex < 0) return

        val current = mediaAt(mediaIndex) ?: return
        val needsHeader = if (mediaIndex == 0) {
            true
        } else {
            val prev = mediaAt(mediaIndex - 1)
            prev == null || !sameDay(prev.dateAdded, current.dateAdded)
        }
        if (needsHeader && isFirstInRow(view, parent)) {
            outRect.top = headerHeightPx
        }
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        parent.layoutManager as? GridLayoutManager ?: return
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val pos = parent.getChildAdapterPosition(child)
            if (pos == RecyclerView.NO_POSITION) continue
            val mediaIndex = pos - leadingItemCount
            if (mediaIndex < 0) continue

            val current = mediaAt(mediaIndex) ?: continue
            val prev = if (mediaIndex > 0) mediaAt(mediaIndex - 1) else null
            val needs = prev == null || !sameDay(prev.dateAdded, current.dateAdded)
            if (!needs) continue
            if (!isFirstInRow(child, parent)) continue

            val label = formatLabel(current.dateAdded * 1000L)
            val textY = child.top - headerHeightPx + headerPaddingTopPx +
                (textPaint.textSize - textPaint.descent())
            c.drawText(
                label,
                headerPaddingStartPx.toFloat(),
                textY,
                textPaint
            )
        }
    }

    private fun mediaAt(index: Int): com.google.photochoice.data.model.MediaFile? {
        val items = mediaAdapter.snapshot().items
        if (index < 0 || index >= items.size) return null
        return items[index]
    }

    private fun isFirstInRow(view: View, parent: RecyclerView): Boolean {
        val lp = view.layoutParams as? GridLayoutManager.LayoutParams ?: return false
        return lp.spanIndex == 0
    }

    private fun formatLabel(timestampMs: Long): String {
        val date = startOfDay(timestampMs)
        return when (date) {
            today -> todayLabel
            yesterday -> yesterdayLabel
            else -> {
                val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
                if (cal.get(Calendar.YEAR) == thisYear) {
                    dayFormat.format(Date(timestampMs))
                } else {
                    yearFormat.format(Date(timestampMs))
                }
            }
        }
    }

    private fun sameDay(secondsA: Long, secondsB: Long): Boolean =
        startOfDay(secondsA * 1000L) == startOfDay(secondsB * 1000L)

    private fun startOfDay(timestampMs: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun startOfToday(): Long = startOfDay(System.currentTimeMillis())

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
