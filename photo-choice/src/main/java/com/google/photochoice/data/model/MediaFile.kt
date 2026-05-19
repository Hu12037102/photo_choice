package com.google.photochoice.data.model

data class MediaFile(
    val id: Long,
    val uri: String,
    val mimeType: String,
    val type: MediaType,
    val dateAdded: Long,
    val width: Int,
    val height: Int,
    val size: Long,
    val duration: Long = 0L,
    val bucketId: String,
    val bucketName: String
) {
    enum class MediaType {
        IMAGE, VIDEO
    }
}
