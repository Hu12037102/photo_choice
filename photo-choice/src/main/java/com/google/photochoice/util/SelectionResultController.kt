package com.google.photochoice.util

import com.google.photochoice.PhotoChoiceResult
import com.google.photochoice.config.CompressConfig
import com.google.photochoice.ui.widget.CompressOverlayView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "完成"结果处理协调器：统一编排 遮罩显隐 + 异步压缩 + 返回取消 + 重入守卫，
 * 交付动作经 [onDeliver] 注入。让每个"完成"入口（网格 / 预览 / 裁剪）都在**自己所在页面**
 * 就地压缩并展示遮罩，压缩完成后再交付，彻底消除"离开发起页才压缩"的不对称。
 *
 * 各页差异仅体现在 [onDeliver]：
 * - 网格页（栈底，直连宿主）：onDeliver = 回传宿主并 finish；
 * - 预览 / 裁剪页（子 Activity）：onDeliver = setResult(已压缩结果) + finish，父页直接交付、不再二次压缩。
 *
 * 线程：压缩在 [Dispatchers.IO] 执行，遮罩与交付在 [scope] 所属线程（主线程）回调。
 */
class SelectionResultController(
    private val scope: CoroutineScope,
    private val overlay: CompressOverlayView,
    private val processor: SelectionResultProcessor,
    /** 处理态变化：true=压缩进行中（调用方据此禁用 Done 按钮、开启返回取消）。 */
    private val onProcessingChanged: (Boolean) -> Unit,
    /** 交付结果（含压缩产物或原始项）；由各页决定回传宿主还是 setResult 回父页。 */
    private val onDeliver: (PhotoChoiceResult) -> Unit
) {

    /** 异步压缩交付进行中：拦截重入（双击 Done / 与其他入口竞态），避免启动多个压缩协程或重复交付。 */
    var isInFlight: Boolean = false
        private set

    /** 当前压缩协程，返回取消时 cancel。 */
    private var compressJob: Job? = null

    /**
     * 提交导出项并按配置处理后交付。
     *
     * - 无需压缩（未开启 / 无可压缩项）：同步组装原始结果并立即交付，不显示遮罩；
     * - 需要压缩：置处理态 → 预约遮罩 → IO 线程压缩 → 收尾隐藏遮罩 → 交付。
     *
     * @return false 表示因重入被拦截（已有压缩在途），调用方无需重复处理。
     */
    fun submit(items: List<SelectionResultProcessor.ExportItem>, config: CompressConfig): Boolean {
        if (isInFlight) return false

        if (!processor.needsCompression(items, config)) {
            // 快路径：无磁盘 IO，直接组装原始结果交付
            onDeliver(processor.assembleDirect(items))
            return true
        }

        isInFlight = true
        onProcessingChanged(true)
        overlay.scheduleShow()
        compressJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                processor.process(items, config)
            }
            // 走到此处说明未被取消（cancel 会抛 CancellationException 跳过后续）
            overlay.hide()
            isInFlight = false
            onProcessingChanged(false)
            onDeliver(result)
        }
        return true
    }

    /**
     * 取消进行中的压缩并恢复 UI（返回键触发）。
     * 仅在压缩在途时生效，返回是否实际取消了一次压缩——调用方据此决定是否拦截返回事件。
     */
    fun cancel(): Boolean {
        if (!isInFlight) return false
        compressJob?.cancel()
        compressJob = null
        isInFlight = false
        overlay.hide()
        onProcessingChanged(false)
        return true
    }
}
