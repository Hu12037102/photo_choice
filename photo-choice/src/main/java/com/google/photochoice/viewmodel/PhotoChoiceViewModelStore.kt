package com.google.photochoice.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.google.photochoice.config.PhotoChoiceConfig

/**
 * 进程级 [PhotoChoiceViewModel] 持有者。
 *
 * 解决跨 Activity（[com.google.photochoice.ui.PhotoChoiceActivity] /
 * [com.google.photochoice.ui.preview.PreviewActivity] /
 * [com.google.photochoice.ui.crop.CropActivity]）共享同一份选择状态的需求；
 * 替代过去用静态 Activity 引用 (`previewHost`) 的方式，避免内存泄漏。
 *
 * 生命周期：
 * - [obtain] 由根 Activity（PhotoChoiceActivity）创建/获取 ViewModel
 * - [peek] 由子 Activity（Preview/Crop）只读获取已存在的 ViewModel；若未创建则返回 null
 * - [release] 必须在根 Activity finish 时调用，清理 ViewModelStore 释放所有内部状态
 *
 * 不是线程安全的；所有调用都应在主线程发生。
 */
internal object PhotoChoiceViewModelStore : ViewModelStoreOwner {

    private val store = ViewModelStore()
    private var current: PhotoChoiceViewModel? = null

    override val viewModelStore: ViewModelStore get() = store

    /** 由根 Activity 调用，首次创建或拿到已存在的 ViewModel。 */
    fun obtain(application: Application, config: PhotoChoiceConfig): PhotoChoiceViewModel {
        val existing = current
        if (existing != null) return existing
        val vm = ViewModelProvider(
            this,
            PhotoChoiceViewModel.Factory(application, config)
        )[PhotoChoiceViewModel::class.java]
        current = vm
        return vm
    }

    /** 子 Activity 调用，只读取已存在的 ViewModel；未创建则返回 null。 */
    fun peek(): PhotoChoiceViewModel? = current

    /** 根 Activity finish 时调用，清理状态。 */
    fun release() {
        current = null
        store.clear()
    }
}
