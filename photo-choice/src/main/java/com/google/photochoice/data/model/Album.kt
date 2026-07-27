package com.google.photochoice.data.model

data class Album(
    val bucketId: String,
    val displayName: String,
    val coverUri: String?,
    val mediaCount: Int,
    val latestDateAdded: Long
)
