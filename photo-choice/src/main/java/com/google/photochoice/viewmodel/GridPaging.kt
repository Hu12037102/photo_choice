package com.google.photochoice.viewmodel

/**
 * 网格分页参数（性能预算见各常量注释）。
 *
 * 设计原则：单次加载更轻、预取提前量够用，而不是无脑加大预取；不对内存中的
 * `MediaFile` 元数据设上限（见 [PhotoChoiceViewModel] 里 `maxSize` 的注释）——它只是
 * 轻量元数据，全量常驻内存代价很小，曾经设过上限反而导致淘汰页无法正确回填、
 * 预览页总数对不上相册真实总数两个正确性问题。
 *
 * | 维度 | 改前 | 本方案 |
 * |------|------|--------|
 * | 单页条数 | 500 + 最多 100 次 XMP IO | 100，翻页零 XMP |
 * | 预取提前量 | 80 条（~20 行） | 140 条（~35 行，约 3 屏） |
 */
internal object GridPaging {

    /** 单页行数：对齐 PRD ~100 条，控制单次 MediaStore 查询与对象分配。 */
    private const val ROWS_PER_PAGE = 25

    /**
     * 预取提前量（行）：约为可见区 3 屏。
     * 过大 → 用户未深滚也频繁查库；过小 → 快滑追不上。
     */
    private const val PREFETCH_SCREEN_ROWS = 35

    /** 首屏优先加载行数：比完整页更小，缩短冷启动首帧时间。 */
    private const val FIRST_PAINT_ROWS = 15

    fun pageSize(spanCount: Int): Int = spanCount * ROWS_PER_PAGE

    fun prefetchDistance(spanCount: Int): Int = spanCount * PREFETCH_SCREEN_ROWS

    fun initialLoadSize(spanCount: Int): Int = spanCount * FIRST_PAINT_ROWS
}
