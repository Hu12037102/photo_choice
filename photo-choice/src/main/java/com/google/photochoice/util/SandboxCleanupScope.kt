package com.google.photochoice.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 沙盒清理的应用级协程作用域。
 *
 * 用于在 [com.google.photochoice.viewmodel.PhotoChoiceViewModel.onCleared] 等
 * viewModelScope 已取消的时机执行磁盘清理：清理任务必须独立于被销毁的组件生命周期，
 * 否则 launch 会立即随作用域取消而不执行。
 *
 * - SupervisorJob：单个清理失败不影响后续清理
 * - Dispatchers.IO：磁盘删除操作，不占主线程
 * - 进程级常驻单例：清理是轻量短任务，无需回收作用域本身
 */
internal object SandboxCleanupScope {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 在 IO 线程执行一次清理任务，不追踪完成（fire-and-forget）。 */
    fun launchCleanup(block: suspend () -> Unit) {
        scope.launch { block() }
    }
}
