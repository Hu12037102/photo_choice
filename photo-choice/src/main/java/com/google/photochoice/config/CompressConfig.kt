package com.google.photochoice.config

data class CompressConfig(
    val enabled: Boolean = false,
    val maxWidth: Int = 1920,
    val maxHeight: Int = 1920,
    val quality: Int = 80
)
