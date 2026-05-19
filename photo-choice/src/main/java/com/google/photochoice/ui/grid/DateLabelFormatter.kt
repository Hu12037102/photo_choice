package com.google.photochoice.ui.grid

import android.content.Context
import android.text.format.DateFormat
import com.google.photochoice.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 将媒体时间戳格式化为相册日期标签（今天 / 昨天 / 本地化月日 / 本地化年月日）。
 */
class DateLabelFormatter(context: Context) {

    private val locale: Locale = Locale.getDefault()
    private val monthDayFormat = SimpleDateFormat(
        DateFormat.getBestDateTimePattern(locale, "MMMd"),
        locale,
    )
    private val yearMonthDayFormat = SimpleDateFormat(
        DateFormat.getBestDateTimePattern(locale, "yMMMd"),
        locale,
    )
    private val todayLabel = context.getString(R.string.photochoice_today)
    private val yesterdayLabel = context.getString(R.string.photochoice_yesterday)
    private val today by lazy { startOfToday() }
    private val yesterday by lazy { today - DAY_MS }
    private val thisYear by lazy { Calendar.getInstance().get(Calendar.YEAR) }

    /** @param timestampMs 毫秒时间戳 */
    fun format(timestampMs: Long): String {
        val dayStart = startOfDay(timestampMs)
        return when (dayStart) {
            today -> todayLabel
            yesterday -> yesterdayLabel
            else -> {
                val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
                if (cal.get(Calendar.YEAR) == thisYear) {
                    monthDayFormat.format(Date(timestampMs))
                } else {
                    yearMonthDayFormat.format(Date(timestampMs))
                }
            }
        }
    }

    /** @param dateAddedSeconds MediaStore DATE_ADDED（秒） */
    fun formatFromDateAddedSeconds(dateAddedSeconds: Long): String =
        format(dateAddedSeconds * 1000L)

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
