package com.google.photochoice.util

import android.util.Log
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 带安全上限的大图解码降采样策略（预览页 / 裁剪页共用）。
 *
 * Glide 默认的 [DownsampleStrategy.CENTER_OUTSIDE] 在全屏无 override 的加载场景下
 * 有两种超限模式，都会解出超过 RecordingCanvas 单张位图 100MB 绘制上限的 Bitmap，
 * 导致该页直接空白，且巨量分配引发内存压力、连带相邻页位图被回收：
 *
 * 1. **小图上采样**（线上实测）：CENTER_OUTSIDE 的缩放系数 = max(目标边/源边)，
 *    对短边远小于 view 的图该系数 > 1，Downsampler 会按系数放大解码——
 *    一张 74KB 的 1440×230 横幅图在 1440×3168 全屏 view 上被放大 13.77 倍，
 *    解码出 19834×3168 ≈ 251MB（与崩溃日志字节数完全吻合）。
 * 2. **巨图不降采样**：全景图/长截图短边已小于 view 对应边时系数为 1，
 *    按原始分辨率全量解码（63MP 的全景图同样 ≈ 251MB）。
 *
 * 本策略叠加三道上限（取最严）：
 * - 系数封顶 1：杜绝解码期上采样（模式 1）——小图按原始尺寸解码，
 *   放大交给 ImageView 的 matrix/GPU 完成，上采样解码本就不产生任何额外细节
 * - 字节预算 [MAX_BYTES]：解码后位图不超过 80MB，对 100MB 硬限留余量（模式 2）
 * - 单边上限 [MAX_EDGE]：不超过 GPU 最大纹理尺寸的安全值，否则无法上屏
 *
 * 普通照片的缩放系数由 CENTER_OUTSIDE 给出且 ≤1、行为不变；仅超限场景被压到上限内。
 */
object CanvasSafeDownsampleStrategy : DownsampleStrategy() {

    private const val TAG = "PhotoChoice/Decode"

    /** 解码字节预算：RecordingCanvas 拒绝绘制超 100MB 的位图，取 80MB 留出余量。 */
    private const val MAX_BYTES = 80L * 1024 * 1024

    /** 单边上限：超过 GPU 最大纹理尺寸的位图无法上屏，8192 为现代主流设备的安全值。 */
    private const val MAX_EDGE = 8192

    override fun getScaleFactor(
        sourceWidth: Int,
        sourceHeight: Int,
        requestedWidth: Int,
        requestedHeight: Int
    ): Float {
        // 源尺寸未知（部分格式头解析失败返回 -1/0）时不干预，交给默认策略
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return CENTER_OUTSIDE.getScaleFactor(
                sourceWidth, sourceHeight, requestedWidth, requestedHeight
            )
        }
        val base = min(
            1f,
            CENTER_OUTSIDE.getScaleFactor(sourceWidth, sourceHeight, requestedWidth, requestedHeight)
        )
        // ARGB_8888 每像素 4 字节；缩放对像素数是平方关系，故预算系数开根号
        val byteScale = sqrt(MAX_BYTES.toDouble() / (4.0 * sourceWidth * sourceHeight)).toFloat()
        val edgeScale = MAX_EDGE.toFloat() / max(sourceWidth, sourceHeight)
        val scale = minOf(base, byteScale, edgeScale)
        if (scale < base) {
            Log.w(
                TAG,
                "decode capped: src=${sourceWidth}x$sourceHeight " +
                    "req=${requestedWidth}x$requestedHeight scale=$base -> $scale"
            )
        }
        return scale
    }

    /** MEMORY：采样值向"更小图"取整，保证结果不越过字节预算。 */
    override fun getSampleSizeRounding(
        sourceWidth: Int,
        sourceHeight: Int,
        requestedWidth: Int,
        requestedHeight: Int
    ): SampleSizeRounding = SampleSizeRounding.MEMORY
}
