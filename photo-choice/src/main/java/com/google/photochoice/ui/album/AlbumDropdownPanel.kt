package com.google.photochoice.ui.album

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
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

/**
 * 标题栏下方的相册下拉面板。
 *
 * 行为：
 * - 锚定在标题栏底边，通过高度 0 → 目标高度展开（自顶向下划出）
 * - 内容高度 ≤ 屏幕 2/3 → 自适应；> 2/3 → 固定 2/3 + 滚动
 * - 面板底边圆角，列表内容裁剪在圆角轮廓内
 */
class AlbumDropdownPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val rvAlbumList: RecyclerView

    private var showing = false
    private var maskView: View? = null
    private var albumAdapter: AlbumListAdapter? = null
    private var onAlbumSelected: ((bucketId: String?, displayName: String) -> Unit)? = null
    private var heightAnimator: ValueAnimator? = null
    private var panelContentHeight = 0

    /** 面板展开/收起时回调，用于标题栏 Chevron 旋转动画。 */
    var onPanelVisibilityChanged: ((expanded: Boolean) -> Unit)? = null

    private val maxPanelHeight: Int
        get() = (Resources.getSystem().displayMetrics.heightPixels *
            DesignTokens.ALBUM_DROPDOWN_MAX_FRACTION).toInt()

    init {
        LayoutInflater.from(context).inflate(R.layout.album_dropdown_panel, this, true)
        rvAlbumList = findViewById(R.id.rvAlbumList)
        rvAlbumList.layoutManager = LinearLayoutManager(context)
        rvAlbumList.itemAnimator = null
        clipChildren = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        visibility = View.GONE
    }

    fun configure(
        albums: List<Album>,
        currentBucketId: String?,
        allPhotosName: String,
        allPhotosCount: Int,
        allPhotosCoverUri: String?,
        onAlbumSelected: (bucketId: String?, displayName: String) -> Unit,
        maskView: View?
    ) {
        this.onAlbumSelected = onAlbumSelected
        if (this.maskView !== maskView) {
            maskView?.setOnClickListener { dismiss() }
            this.maskView = maskView
        }

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

        translationY = 0f
        alpha = 1f
        visibility = View.VISIBLE
        setPanelHeight(0)

        animatePanelHeight(
            from = 0,
            to = target,
            duration = DesignTokens.ALBUM_DROPDOWN_ANIM_SHOW_MS,
            interpolator = DecelerateInterpolator()
        )

        maskView?.let {
            it.alpha = 0f
            it.visibility = View.VISIBLE
            it.animate().cancel()
            it.animate()
                .alpha(1f)
                .setDuration(DesignTokens.ALBUM_DROPDOWN_ANIM_SHOW_MS)
                .setListener(null)
                .start()
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

        maskView?.let {
            it.animate().cancel()
            it.animate()
                .alpha(0f)
                .setDuration(DesignTokens.ALBUM_DROPDOWN_ANIM_DISMISS_MS)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        it.visibility = View.GONE
                    }
                })
                .start()
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
                override fun onAnimationEnd(animation: Animator) {
                    heightAnimator = null
                    onEnd()
                }

                override fun onAnimationCancel(animation: Animator) {
                    heightAnimator = null
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
