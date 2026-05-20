package com.google.photochoice.util

import android.content.Context
import android.view.View

/** 把 dp 转为像素（int）。 */
fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()
fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
fun View.dp(value: Float): Int = context.dp(value)
fun View.dp(value: Int): Int = context.dp(value)
