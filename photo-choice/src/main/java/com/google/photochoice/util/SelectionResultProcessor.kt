package com.google.photochoice.util

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.google.photochoice.PhotoChoiceResult
import com.google.photochoice.config.CompressConfig

/**
 * 选择结果处理器：对导出项按配置执行图片压缩后组装 [PhotoChoiceResult]。
 *
 * 纯逻辑、无 UI 依赖，三个"完成"入口（网格 / 预览 / 裁剪）共用同一份压缩与路径解析逻辑，
 * 消除过去压缩逻辑仅存在于 PhotoChoiceActivity 一处、子页面被迫"回宿主页再压缩"的不对称。
 *
 * 线程：[process] 内含 Bitmap 解码/编码等磁盘 IO，调用方须在 IO 线程执行；
 * [assembleDirect] 仅做 URI/路径字符串解析，可在主线程执行。
 */
class SelectionResultProcessor(private val context: Context) {

    /** 单条导出项：源 URI + 是否需执行压缩。 */
    data class ExportItem(val uri: Uri, val shouldCompress: Boolean)

    /**
     * 组装未压缩结果：所有项按原 URI 直出，仅解析可回传路径。
     * 用于"未开启压缩"或"无任何项需压缩"的快路径，无磁盘 IO。
     */
    fun assembleDirect(items: List<ExportItem>): PhotoChoiceResult =
        PhotoChoiceResult(
            uris = items.map { it.uri },
            paths = items.map { resolvePath(it.uri) }
        )

    /**
     * 逐项处理并组装结果：需压缩项写入沙盒 JPEG，失败回退原 URI；其余项原样直出。
     * 含 Bitmap 解码/编码，务必在 IO 线程调用。
     */
    fun process(items: List<ExportItem>, config: CompressConfig): PhotoChoiceResult {
        val helper = CompressHelper(context)
        val outUris = mutableListOf<Uri>()
        val outPaths = mutableListOf<String>()
        for (item in items) {
            if (item.shouldCompress) {
                val file = helper.compress(item.uri, config)
                if (file != null) {
                    outUris.add(Uri.fromFile(file))
                    outPaths.add(file.absolutePath)
                } else {
                    // 压缩失败回退原 URI，保证该条目仍可回传，不丢结果
                    outUris.add(item.uri)
                    outPaths.add(resolvePath(item.uri))
                }
            } else {
                outUris.add(item.uri)
                outPaths.add(resolvePath(item.uri))
            }
        }
        return PhotoChoiceResult(uris = outUris, paths = outPaths)
    }

    /**
     * 是否存在需要实际压缩的项：仅当开启压缩且至少一项标记需压缩时为 true。
     * 决定"完成"是否进入异步压缩+遮罩流程。
     */
    fun needsCompression(items: List<ExportItem>, config: CompressConfig): Boolean =
        config.enabled && items.any { it.shouldCompress }

    /**
     * paths 语义：仅库产物（压缩/裁剪的 file://）保证是可直接打开的文件路径；
     * 原始媒体（content://）在分区存储下无可靠文件路径（DATA 列已废弃且常为 null），
     * 统一返回 URI 字符串——宿主对原始媒体应使用 uris + ContentResolver 读取。
     */
    fun resolvePath(uri: Uri): String {
        if (uri.scheme == "file") return uri.path ?: uri.toString()
        return uri.toString()
    }

    companion object {
        /** 从内部传递的 URI/paths 字符串还原导出项（子页面已压缩结果回传父页用）。 */
        fun toUriList(raw: List<String>): List<Uri> = raw.map { it.toUri() }
    }
}
