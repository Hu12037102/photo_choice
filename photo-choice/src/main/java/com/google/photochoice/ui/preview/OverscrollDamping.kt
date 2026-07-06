package com.google.photochoice.ui.preview

/**
 * 对越界方向的拖拽增量施加渐进阻尼。
 *
 * 已越界越多，本帧允许通过的增量占比越小（[overshoot] 趋近 [maxOvershoot] 时趋于 0），
 * 使橡皮筋效果随手指持续拖拽越拉越远；不会像"每帧固定拉回已越界位移的一部分"那样，
 * 在几帧内就收敛到一个和继续拖拽距离无关的极小定值——那样会让人感觉"拖不动"，
 * 松手时也几乎看不出回弹。
 *
 * @param delta 本帧手指在该轴上的原始位移
 * @param overshoot 本帧位移施加前、沿 [delta] 方向已经越界的位移量；未越界或正朝合法
 *   区域移动时应传 0（此时不需要阻尼，1:1 跟手）
 * @param maxOvershoot 允许的最大越界位移（渐近上限），非正数时视为不设上限（不阻尼）
 */
internal fun dampOverscrollDelta(delta: Float, overshoot: Float, maxOvershoot: Float): Float {
    if (overshoot <= 0f || maxOvershoot <= 0f) return delta
    val progress = (overshoot / maxOvershoot).coerceIn(0f, 1f)
    return delta * (1f - progress)
}

/**
 * 计算某一轴上、沿拖拽方向已经越界的位移量。
 *
 * 内容在该轴上的范围是 `[contentStart, contentEnd]`，可视区域范围是 `[0, viewSize]`。
 * - [delta] 为正（向下/向右拖）：只有内容起点已经被拖过 0（`contentStart > 0`，
 *   顶边/左边已经露出空白）才算越界，越界量即 [contentStart]；只要 `contentStart <= 0`
 *   （还有内容在起点方向等待显示）就是合法滚动，不算越界。
 * - [delta] 为负（向上/向左拖）：只有内容终点已经被拖过 [viewSize]（`contentEnd < viewSize`，
 *   底边/右边已经露出空白）才算越界，越界量即 `viewSize - contentEnd`；只要
 *   `contentEnd >= viewSize`（还有内容在终点方向等待显示，例如长图正文还没滑到底）
 *   就是合法滚动，不算越界。
 *
 * 内容本身不超出可视区域（`contentEnd - contentStart <= viewSize`）或 [delta] 为 0 时，
 * 始终返回 0。
 */
internal fun computeOvershoot(contentStart: Float, contentEnd: Float, viewSize: Float, delta: Float): Float {
    if (contentEnd - contentStart <= viewSize) return 0f
    return when {
        delta > 0f -> contentStart.coerceAtLeast(0f)
        delta < 0f -> (viewSize - contentEnd).coerceAtLeast(0f)
        else -> 0f
    }
}
