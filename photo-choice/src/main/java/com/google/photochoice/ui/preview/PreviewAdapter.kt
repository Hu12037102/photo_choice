package com.google.photochoice.ui.preview

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.photochoice.data.model.MediaFile

class PreviewAdapter(
    fragment: Fragment,
    private val mediaList: List<MediaFile>
) : FragmentStateAdapter(fragment) {

    override fun createFragment(position: Int): Fragment =
        PreviewPageFragment.newInstance(mediaList[position])

    override fun getItemCount(): Int = mediaList.size

    override fun getItemId(position: Int): Long = mediaList[position].id

    override fun containsItem(itemId: Long): Boolean =
        mediaList.any { it.id == itemId }

    fun getMediaAt(position: Int): MediaFile? = mediaList.getOrNull(position)
}
