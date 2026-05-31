package com.google.photochoice.ui.album

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.google.photochoice.R
import com.google.photochoice.config.DesignTokens
import com.google.photochoice.data.model.Album

class AlbumDropdownLayer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val maskView: View
    private val panel: AlbumDropdownPanel

    var onPanelVisibilityChanged: ((expanded: Boolean) -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.album_dropdown_layer, this, true)
        maskView = findViewById(R.id.albumDropdownMask)
        panel = findViewById(R.id.albumDropdownPanel)
        visibility = View.GONE
        maskView.setOnClickListener { dismiss() }
        panel.onPanelVisibilityChanged = { expanded ->
            if (expanded) {
                showMask()
            } else {
                hideMask()
            }
            onPanelVisibilityChanged?.invoke(expanded)
        }
    }

    fun configure(
        albums: List<Album>,
        currentBucketId: String?,
        allPhotosName: String,
        allPhotosCount: Int,
        allPhotosCoverUri: String?,
        onAlbumSelected: (bucketId: String?, displayName: String) -> Unit
    ) {
        panel.configure(
            albums = albums,
            currentBucketId = currentBucketId,
            allPhotosName = allPhotosName,
            allPhotosCount = allPhotosCount,
            allPhotosCoverUri = allPhotosCoverUri,
            onAlbumSelected = onAlbumSelected
        )
    }

    fun updateSelection(currentBucketId: String?) {
        panel.updateSelection(currentBucketId)
    }

    fun show() {
        if (panel.isShowing()) return
        visibility = View.VISIBLE
        panel.show()
        if (!panel.isShowing()) {
            visibility = View.GONE
        }
    }

    fun dismiss() {
        if (!panel.isShowing()) return
        panel.dismiss()
    }

    fun toggle() {
        if (panel.isShowing()) dismiss() else show()
    }

    fun isShowing(): Boolean = panel.isShowing()

    // region 预测性返回

    fun onBackGestureStarted() {
        maskView.animate().cancel()
        panel.onBackGestureStarted()
    }

    fun setDismissProgress(progress: Float) {
        maskView.alpha = 1f - progress.coerceIn(0f, 1f)
        panel.setDismissProgress(progress)
    }

    fun commitBackDismiss() {
        panel.commitBackDismiss()
    }

    fun cancelBackDismiss() {
        panel.cancelBackDismiss()
        maskView.animate().cancel()
        maskView.animate().alpha(1f).setDuration(150).setListener(null).start()
    }

    // endregion

    private fun showMask() {
        maskView.animate().cancel()
        maskView.alpha = if (maskView.visibility == View.VISIBLE) maskView.alpha else 0f
        maskView.visibility = View.VISIBLE
        maskView.animate()
            .alpha(1f)
            .setDuration(DesignTokens.ALBUM_DROPDOWN_ANIM_SHOW_MS)
            .setListener(null)
            .start()
    }

    private fun hideMask() {
        maskView.animate().cancel()
        maskView.animate()
            .alpha(0f)
            .setDuration(DesignTokens.ALBUM_DROPDOWN_ANIM_DISMISS_MS)
            .setListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) {
                        maskView.visibility = View.GONE
                        visibility = View.GONE
                    }
                }
            })
            .start()
    }

    override fun onDetachedFromWindow() {
        maskView.animate().cancel()
        super.onDetachedFromWindow()
    }
}
