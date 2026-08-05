package com.google.photochoice.util

import android.util.Log
import com.google.photochoice.BuildConfig

/**
 * 库内统一日志出口。
 *
 * 分级策略——按「release 包是否需要这条信息」划分，而非按信息重要程度：
 * - [d]：流程进度、命中率、耗时等调试信息，**仅 Debug 构建输出**。这类日志在正常路径上
 *   高频触发（如逐相册预建、逐条索引落盘），release 包输出既有性能损耗，也会把内部
 *   缓存目录、相册 bucketId 等路径信息暴露到宿主 logcat 里。
 * - [w] / [e]：降级与失败信息，**所有构建都输出**。宿主接入后线上排查问题只能靠这些，
 *   且仅在异常路径触发，频率天然可控。
 *
 * 字符串拼接放在调用点会先于 [BuildConfig.DEBUG] 判断执行，因此 [d] 收 lambda 而非
 * String——release 包下 lambda 不被调用，拼接开销一并省掉。
 *
 * 与 [MediaLoadLogger] 的分工：后者是媒体列表专用的结构化明细日志（成批打印条目字段），
 * 本类是全库通用的单条日志出口。
 */
internal object PhotoChoiceLog {

    /** 调试日志，仅 Debug 构建输出；message 惰性求值，release 下不产生拼接开销。 */
    inline fun d(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(tag, message())
    }

    /** 警告日志，所有构建输出：降级路径的线上排查依赖它。 */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
    }

    /** 错误日志，所有构建输出。 */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }
}
