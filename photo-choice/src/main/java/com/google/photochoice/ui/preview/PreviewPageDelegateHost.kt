package com.google.photochoice.ui.preview

import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import com.google.photochoice.data.model.MediaFile

/**
 * [PreviewPageDelegate] 访问 Fragment 生命周期与预览参数的宿主。
 */
internal class PreviewPageDelegateHost(
    private val fragment: Fragment,
    val mediaId: Long,
    val uri: String,
    var isMotionPhoto: Boolean,
) {
    val context get() = fragment.requireContext()
    val resources get() = fragment.resources
    val lifecycleScope: LifecycleCoroutineScope
        get() = fragment.viewLifecycleOwner.lifecycleScope

    fun probeMediaFile(): MediaFile = MediaFile(
        id = mediaId,
        uri = uri,
        mimeType = "image/jpeg",
        type = MediaFile.MediaType.IMAGE,
        dateAdded = 0L,
        width = 0,
        height = 0,
        size = 0L,
        bucketId = "",
        bucketName = "",
        isMotionPhoto = isMotionPhoto,
    )

    fun notifyLivePhotoDetected() {
        (fragment.activity as? PreviewPageFragment.LivePhotoBadgeHost)
            ?.onLivePhotoDetected(mediaId)
    }

    fun sharedMotionPhotoPlayer(): PreviewMotionPhotoPlayer? =
        (fragment.activity as? MotionPhotoPlaybackOwner)?.motionPhotoPlayer
}