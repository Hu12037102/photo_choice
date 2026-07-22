package com.google.photochoice.ui.preview

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.photochoice.data.model.MediaFile

/**
 * 预览页 ViewPager 适配器。
 *
 * 列表可增长：从网格进入预览时初始数据只是网格已加载的分页快照，
 * 滑近末尾由 ViewModel 续载后经 [append] 尾部追加（见
 * [com.google.photochoice.viewmodel.PhotoChoiceViewModel.onPreviewPageSelected]）。
 */
class PreviewAdapter(
    activity: FragmentActivity,
    initialMediaList: List<MediaFile>
) : FragmentStateAdapter(activity) {

    private val mediaList = initialMediaList.toMutableList()

    override fun createFragment(position: Int): Fragment =
        PreviewPageFragment.newInstance(mediaList[position])

    override fun getItemCount(): Int = mediaList.size

    override fun getItemId(position: Int): Long = mediaList[position].id

    override fun containsItem(itemId: Long): Boolean =
        mediaList.any { it.id == itemId }

    /** 返回指定位置的媒体项；越界返回 null。 */
    fun getMediaAt(position: Int): MediaFile? = mediaList.getOrNull(position)

    /**
     * 追加续载的分页数据（仅尾部追加，不支持中间插入/删除）。
     * 调用方需保证 [newItems] 与现有列表无重复 id
     * （FragmentStateAdapter 要求 itemId 稳定且唯一，重复会导致页面复用错乱）。
     */
    fun append(newItems: List<MediaFile>) {
        if (newItems.isEmpty()) return
        val start = mediaList.size
        mediaList.addAll(newItems)
        notifyItemRangeInserted(start, newItems.size)
    }
}
