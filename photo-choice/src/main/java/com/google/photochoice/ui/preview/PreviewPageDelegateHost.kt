package com.google.photochoice.ui.preview

import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope

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

    fun notifyLivePhotoDetected() {
        (fragment.activity as? PreviewPageFragment.LivePhotoBadgeHost)
            ?.onLivePhotoDetected(mediaId)
    }

    fun sharedMotionPhotoPlayer(): PreviewMotionPhotoPlayer? =
        (fragment.activity as? MotionPhotoPlaybackOwner)?.motionPhotoPlayer
}
