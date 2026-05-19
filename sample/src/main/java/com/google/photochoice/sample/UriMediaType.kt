package com.google.photochoice.sample

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

internal fun Uri.isVideo(context: Context): Boolean {
    val mime = context.contentResolver.getType(this)
    if (mime != null) return mime.startsWith("video/")
    return lastPathSegment?.contains("video", ignoreCase = true) == true
}

internal fun Uri.displayName(context: Context): String {
    context.contentResolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) {
                return cursor.getString(index)
            }
        }
    return toString()
}
