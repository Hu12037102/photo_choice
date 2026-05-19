package com.google.photochoice.config

data class CropConfig(
    val enabled: Boolean = false,
    val aspectRatio: CropAspectRatio = CropAspectRatio.ORIGINAL,
    val maxWidth: Int = 0,
    val maxHeight: Int = 0
)
