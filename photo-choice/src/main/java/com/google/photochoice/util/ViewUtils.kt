package com.google.photochoice.util

import android.content.Context
import android.content.res.Resources
import android.util.TypedValue
import android.view.View

/** 把 dp 转为像素（int）。 */
fun Context.dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()
fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
fun View.dp(value: Float): Int = context.dp(value)
fun View.dp(value: Int): Int = context.dp(value)

/** 把 dp 转为像素（float）。 */
fun Context.dpF(value: Float): Float = value * resources.displayMetrics.density

/** 把 sp 转为像素。 */
fun Context.sp(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

/** 主题色获取（attr → ColorInt）。 */
fun Context.themeColor(attr: Int): Int {
    val tv = TypedValue()
    theme.resolveAttribute(attr, tv, true)
    return tv.data
}

val Resources.screenHeightPx: Int get() = displayMetrics.heightPixels
val Resources.screenWidthPx: Int get() = displayMetrics.widthPixels
