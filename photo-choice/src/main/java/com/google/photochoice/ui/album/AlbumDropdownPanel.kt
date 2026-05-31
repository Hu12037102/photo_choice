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

        // 先设为 VISIBLE 触发 parent 布局，但保持 0 高度不可见。
        // post 到下一帧等 layout 完成后再测量，确保 maxPanelHeight
        // 读到的 parent.height 是稳定真实值，而非 GONE 状态的 0。
        visibility = View.VISIBLE
        translationY = 0f
        alpha = 1f
        setPanelHeight(0)

        post {
            if (!showing) return@post

            panelContentHeight = measurePanelContentHeight()
            val target = panelContentHeight.coerceAtMost(maxPanelHeight).coerceAtLeast(1)

            animatePanelHeight(
                from = 0,
                to = target,
                duration = DesignTokens.ALBUM_DROPDOWN_ANIM_SHOW_MS,
                interpolator = DecelerateInterpolator()
            )
        }
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

    /**
     * 计算面板内容高度。
     * 直接由 item 数量 × 固定 item 高度得出，避免依赖 RecyclerView.measure()，
     * 后者在父容器 GONE / 二次测量等场景下返回不一致的值。
     */
    private fun measurePanelContentHeight(): Int {
        val itemCount = albumAdapter?.itemCount ?: 0
        if (itemCount == 0) return 1
        val itemHeightPx = (ITEM_HEIGHT_DP * resources.displayMetrics.density).toInt()
        return itemCount * itemHeightPx
    }

    companion object {
        /** item_album.xml 中定义的固定行高 */
        private const val ITEM_HEIGHT_DP = 68f
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
