package com.google.photochoice.viewmodel

/**
 * 网格分页参数（性能预算见各常量注释）。
 *
 * 设计原则：首屏一次拉足 500 条（进入即有充足数据，首屏静止时不再触发连环追加查询，
 * 预览快照初始覆盖面也更大）；翻页单页更轻、预取提前量够用。不对内存中的 `MediaFile`
 * 元数据设上限（见 [PhotoChoiceViewModel] 里 `maxSize` 的注释）——它只是轻量元数据，
 * 全量常驻内存代价很小，曾经设过上限反而导致淘汰页无法正确回填、预览页总数对不上
 * 相册真实总数两个正确性问题。
 *
 * | 维度 | 取值 | 说明 |
 * |------|------|------|
 * | 首屏加载 | 500 条（对齐整行） | 单次 MediaStore 元数据查询，角标嗅探全异步、无同步 XMP IO |
 * | 单页条数 | 25 行（span=4 时 100 条） | 控制翻页时单次查询与对象分配 |
 * | 预取提前量 | 35 行（约 3 屏） | 过大则未深滚也频繁查库，过小则快滑追不上 |
 */
internal object GridPaging {

    /** 单页行数：对齐 PRD ~100 条，控制单次 MediaStore 查询与对象分配。 */
    private const val ROWS_PER_PAGE = 25

    /**
     * 预取提前量（行）：约为可见区 3 屏。
     * 过大 → 用户未深滚也频繁查库；过小 → 快滑追不上。
     */
    private const val PREFETCH_SCREEN_ROWS = 35

    /**
     * 首屏一次性加载条数：500 条基本覆盖常规浏览深度，首屏静止时不会再触发
     * 预取追加（500 远大于预取提前量），进预览的初始快照也更完整。
     * 现在实况角标嗅探全异步（enricher / DB 标记），加大首批量不引入同步 XMP IO。
     */
    private const val INITIAL_LOAD_ITEMS = 500

    /** 翻页单页条数 = 列数 × 单页行数。 */
    fun pageSize(spanCount: Int): Int = spanCount * ROWS_PER_PAGE

    /** 预取提前量（条）= 列数 × 预取行数。 */
    fun prefetchDistance(spanCount: Int): Int = spanCount * PREFETCH_SCREEN_ROWS

    /** 首屏加载条数：固定 500 条并向上取整对齐整行，避免首批数据末行缺格。 */
    fun initialLoadSize(spanCount: Int): Int =
        (INITIAL_LOAD_ITEMS + spanCount - 1) / spanCount * spanCount
}
