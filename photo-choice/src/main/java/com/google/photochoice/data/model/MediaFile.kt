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
    val bucketName: String,
    /** 文件名（含扩展名），用于零 I/O 的实况图启发式判定。 */
    val displayName: String = "",
    /** Motion Photo / 实况图（仍为 IMAGE 类型，含内嵌短视频）。 */
    val isMotionPhoto: Boolean = false
) {
    enum class MediaType {
        IMAGE, VIDEO
    }
}
