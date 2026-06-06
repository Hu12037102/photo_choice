package com.google.photochoice.ui.preview

/** [PreviewActivity] 向图片预览 delegate 提供共享实况播放器。 */
internal interface MotionPhotoPlaybackOwner {
    val motionPhotoPlayer: PreviewMotionPhotoPlayer

    /** 指定 mediaId 是否为当前正在展示的预览页（用于首次自动播放门控，离屏页返回 false）。 */
    fun isCurrentPreviewPage(mediaId: Long): Boolean
}
