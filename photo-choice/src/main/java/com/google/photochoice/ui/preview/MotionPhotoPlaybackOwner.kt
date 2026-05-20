package com.google.photochoice.ui.preview

/** [PreviewActivity] 向图片预览 delegate 提供共享实况播放器。 */
internal interface MotionPhotoPlaybackOwner {
    val motionPhotoPlayer: PreviewMotionPhotoPlayer
}
