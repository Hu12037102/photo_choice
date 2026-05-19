package com.google.photochoice

import android.net.Uri

data class PhotoChoiceResult(
    val uris: List<Uri>,
    val paths: List<String>,
    val isOriginal: Boolean = false
)
