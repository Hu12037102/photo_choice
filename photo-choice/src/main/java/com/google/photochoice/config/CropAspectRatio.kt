package com.google.photochoice.config

enum class CropAspectRatio(val ratio: Float?) {
    ORIGINAL(null),
    SQUARE(1f),
    RATIO_3_4(3f / 4f),
    RATIO_4_3(4f / 3f),
    RATIO_9_16(9f / 16f),
    RATIO_16_9(16f / 9f)
}
