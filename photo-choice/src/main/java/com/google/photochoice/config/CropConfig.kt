package com.google.photochoice.config

import java.io.Serializable

data class CropConfig(
    val enabled: Boolean = false,
    val aspectRatio: CropAspectRatio = CropAspectRatio.ORIGINAL,
    val maxWidth: Int = 0,
    val maxHeight: Int = 0
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
