package com.google.photochoice.ui.preview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import com.google.photochoice.data.model.MediaFile

class PreviewPageFragment : Fragment() {

    private var delegateHost: PreviewPageDelegateHost? = null
    private var pageDelegate: PreviewPageDelegate? = null

    companion object {
        private const val ARG_URI = "uri"
        private const val ARG_TYPE = "type"
        private const val ARG_MEDIA_ID = "media_id"
        private const val ARG_MOTION_PHOTO = "motion_photo"

        fun newInstance(mediaFile: MediaFile): PreviewPageFragment {
            return PreviewPageFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_URI, mediaFile.uri)
                    putString(ARG_TYPE, mediaFile.type.name)
                    putLong(ARG_MEDIA_ID, mediaFile.id)
                    putBoolean(ARG_MOTION_PHOTO, mediaFile.isMotionPhoto)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val uri = arguments?.getString(ARG_URI) ?: return FrameLayout(requireContext())
        val type = arguments?.getString(ARG_TYPE) ?: MediaFile.MediaType.IMAGE.name
        val isVideo = type == MediaFile.MediaType.VIDEO.name
        val isMotionPhoto = !isVideo && arguments?.getBoolean(ARG_MOTION_PHOTO, false) == true
        val mediaId = arguments?.getLong(ARG_MEDIA_ID, 0L) ?: 0L

        val host = PreviewPageDelegateHost(
            fragment = this,
            mediaId = mediaId,
            uri = uri,
            isMotionPhoto = isMotionPhoto,
        )
        delegateHost = host
        pageDelegate = if (isVideo) {
            PreviewVideoPageDelegate(host, uri)
        } else {
            PreviewImagePageDelegate(host, uri)
        }
        return pageDelegate!!.onCreateView(inflater, container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pageDelegate?.onViewCreated()
    }

    fun isVideoPage(): Boolean = pageDelegate?.isVideoPage() == true

    fun isMotionPhotoPage(): Boolean = pageDelegate?.isMotionPhotoPage() == true

    fun setOnSingleTapListener(listener: () -> Unit) {
        pageDelegate?.setOnSingleTapListener(listener)
    }

    fun setOnZoomInteractionListener(listener: (zoomed: Boolean, scaling: Boolean) -> Unit) {
        pageDelegate?.setOnZoomInteractionListener(listener)
    }

    fun syncChromeFromHost(fullscreen: Boolean, animated: Boolean) {
        pageDelegate?.syncChromeFromHost(fullscreen, animated)
    }

    fun isZoomed(): Boolean = pageDelegate?.isZoomed() == true

    fun pauseVideo() {
        pageDelegate?.pauseVideo()
    }

    fun playVideo() {
        pageDelegate?.playVideo()
    }

    override fun onPause() {
        super.onPause()
        pageDelegate?.onPause()
    }

    override fun onDestroyView() {
        pageDelegate?.onDestroyView()
        pageDelegate = null
        delegateHost = null
        super.onDestroyView()
    }

    /** 预览页通知 Activity 展示实况角标。 */
    interface LivePhotoBadgeHost {
        fun onLivePhotoDetected(mediaId: Long)
    }
}
