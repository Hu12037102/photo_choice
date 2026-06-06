package com.google.photochoice.ui.preview

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

/**
 * 预览页单页内容委托：图片与视频各自实现，[PreviewPageFragment] 仅转发。
 */
internal interface PreviewPageDelegate {
    fun onCreateView(inflater: LayoutInflater, container: ViewGroup?): View
    fun onViewCreated()
    fun onPause()
    fun onDestroyView()
    fun setOnSingleTapListener(listener: () -> Unit)
    fun setOnZoomInteractionListener(listener: (zoomed: Boolean, scaling: Boolean) -> Unit)
    fun syncChromeFromHost(fullscreen: Boolean, animated: Boolean)
    fun isZoomed(): Boolean
    fun pauseVideo()
    fun playVideo()
    fun isVideoPage(): Boolean
    fun isMotionPhotoPage(): Boolean
    /** 页面可见性变化：切换到此页时 visible=true，离开此页时 visible=false。 */
    fun onPageVisibilityChanged(visible: Boolean) {}
}
