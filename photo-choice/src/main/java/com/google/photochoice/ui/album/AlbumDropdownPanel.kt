package com.google.photochoice.ui.album

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.photochoice.R
import com.google.photochoice.config.DesignTokens
import com.google.photochoice.data.model.Album

class AlbumDropdownPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val rvAlbumList: RecyclerView

    private var showing = false
    private var albumAdapter: AlbumListAdapter? = null
    private var onAlbumSelected: ((bucketId: String?, displayName: String) -> Unit)? = null
    private var heightAnimator: ValueAnimator? = null
    private var panelContentHeight = 0

    var onPanelVisibilityChanged: ((expanded: Boolean) -> Unit)? = null

    private val maxPanelHeight: Int
        get() {
            val fallbackHeight = Resources.getSystem().displayMetrics.heightPixels
            val availableHeight = ((parent as? ViewGroup)?.height ?: 0)
                .takeIf { it > 0 } ?: fallbackHeight
            return (availableHeight * DesignTokens.ALBUM_DROPDOWN_MAX_FRACTION).toInt()
        }

    init {
        LayoutInflater.from(context).inflate(R.layout.album_dropdown_panel, this, true)
        rvAlbumList = findViewById(R.id.rvAlbumList)
        rvAlbumList.layoutManager = LinearLayoutManager(context)
        rvAlbumList.itemAnimator = null
        clipChildren = true
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        visibility = View.GONE
    }

    fun configure(
        albums: List<Album>,
        currentBucketId: String?,
        allPhotosName: String,
        allPhotosCount: Int,
        allPhotosCoverUri: String?,
        onAlbumSelected: (bucketId: String?, displayName: String) -> Unit
    ) {
        this.onAlbumSelected = onAlbumSelected

        val adapter = albumAdapter ?: AlbumListAdapter(
            currentBucketId = currentBucketId,
            allPhotosName = allPhotosName,
            allPhotosCount = allPhotosCount,
            allPhotosCoverUri = allPhotosCoverUri,
            onItemClick = { bucketId, displayName ->
                this.onAlbumSelected?.invoke(bucketId, displayName)
            }
        ).also {
            albumAdapter = it
            rvAlbumList.adapter = it
        }

        adapter.currentBucketId = currentBucketId
        adapter.allPhotosName = allPhotosName
        adapter.allPhotosCount = allPhotosCount
        adapter.allPhotosCoverUri = allPhotosCoverUri
        adapter.submitList(albums)
    }

    fun updateSelection(currentBucketId: String?) {
        albumAdapter?.let { adapter ->
            adapter.currentBucketId = currentBucketId
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }
    }

    fun show() {
        if (showing) return
        if (albumAdapter == null || albumAdapter!!.itemCount == 0) return
        showing = true
        onPanelVisibilityChanged?.invoke(true)

        panelContentHeight = measurePanelContentHeight()
        val target = panelContentHeight.coerceAtMost(maxPanelHeight).coerceAtLeast(1)
        val from = if (isVisible) height.coerceAtLeast(0) else 0

        translationY = 0f
        alpha = 1f
        visibility = View.VISIBLE
        setPanelHeight(from)

        animatePanelHeight(
            from = from,
            to = target,
            duration = DesignTokens.ALBUM_DROPDOWN_ANIM_SHOW_MS,
            interpolator = DecelerateInterpolator()
        )
    }

    fun dismiss() {
        if (!showing) return
        showing = false
        onPanelVisibilityChanged?.invoke(false)

        val current = height.coerceAtLeast(0)
        animatePanelHeight(
            from = current,
            to = 0,
            duration = DesignTokens.ALBUM_DROPDOWN_ANIM_DISMISS_MS,
            interpolator = AccelerateInterpolator()
        ) {
            visibility = View.GONE
            setPanelHeight(0)
        }
    }

    fun toggle() {
        if (showing) dismiss() else show()
    }

    fun isShowing(): Boolean = showing

    private fun measurePanelContentHeight(): Int {
        val w = if (width > 0) width else Resources.getSystem().displayMetrics.widthPixels
        val widthSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        rvAlbumList.measure(widthSpec, heightSpec)
        return rvAlbumList.measuredHeight.coerceAtLeast(1)
    }

    private fun setPanelHeight(heightPx: Int) {
        val lp = layoutParams ?: return
        lp.height = heightPx
        layoutParams = lp
    }

    private fun animatePanelHeight(
        from: Int,
        to: Int,
        duration: Long,
        interpolator: Interpolator,
        onEnd: () -> Unit = {}
    ) {
        heightAnimator?.cancel()
        if (from == to) {
            setPanelHeight(to)
            onEnd()
            return
        }
        heightAnimator = ValueAnimator.ofInt(from, to).apply {
            this.duration = duration
            this.interpolator = interpolator
            addUpdateListener { animator ->
                setPanelHeight(animator.animatedValue as Int)
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                    heightAnimator = null
                }
                override fun onAnimationEnd(animation: Animator) {
                    heightAnimator = null
                    if (!cancelled) onEnd()
                }
            })
            start()
        }
    }

    override fun onDetachedFromWindow() {
        heightAnimator?.cancel()
        heightAnimator = null
        super.onDetachedFromWindow()
    }
}
