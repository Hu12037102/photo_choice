package com.google.photochoice.config

/**
 * PhotoChoice 全量配置。所有字段均为不可变；通过 Builder 或 copy() 创建。
 *
 * 选择数量语义：
 * - [selectCount] == 1：单选
 * - [selectCount] > 1：多选
 * 合法范围 1..9，超出区间会被自动 clamp 到 1。
 *
 * 防御性规整原则：公共 API 对越界/无效入参一律**回退修正**而非抛异常，
 * 库不因宿主传参失误产生 Crash；内部消费方统一使用 sanitizedXxx / effectiveXxx 属性。
 */
data class PhotoChoiceConfig(
    val selectCount: Int = 9,
    val mediaType: MediaType = MediaType.IMAGE,
    val spanCount: Int = 4,
    val showCamera: Boolean = true,
    /** 图片体积下限（字节）。用于过滤图标类小图；0 = 不限制。仅作用于图片，视频不受影响。 */
    val minImageSize: Long = 0L,
    /** 图片体积上限（字节）。用于过滤超大图；Long.MAX_VALUE = 不限制。仅作用于图片，视频不受影响。 */
    val maxImageSize: Long = Long.MAX_VALUE,
    val minVideoDurationMs: Long = 0L,
    val maxVideoDurationMs: Long = 60_000L,
    val themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    val cropConfig: CropConfig = CropConfig(),
    val compressConfig: CompressConfig = CompressConfig()
) : java.io.Serializable {
    companion object {
        private const val serialVersionUID = 1L
        const val SELECT_COUNT_MIN = 1
        const val SELECT_COUNT_MAX = 9
        const val SPAN_COUNT_MIN = 2
        const val SPAN_COUNT_MAX = 6
    }

    /** 安全的 selectCount：超出 [SELECT_COUNT_MIN]..[SELECT_COUNT_MAX] 区间时回退到 1。 */
    val sanitizedSelectCount: Int =
        if (selectCount in SELECT_COUNT_MIN..SELECT_COUNT_MAX) selectCount else SELECT_COUNT_MIN

    val isSingleSelect: Boolean get() = sanitizedSelectCount == 1

    /** 安全的 spanCount：越界时 clamp 到 [SPAN_COUNT_MIN]..[SPAN_COUNT_MAX]，不抛异常。 */
    val sanitizedSpanCount: Int = spanCount.coerceIn(SPAN_COUNT_MIN, SPAN_COUNT_MAX)

    /** 规整后的视频时长下限：宿主误传 min > max 时自动交换。 */
    val sanitizedMinVideoDurationMs: Long =
        minOf(minVideoDurationMs, maxVideoDurationMs).coerceAtLeast(0L)

    /** 规整后的视频时长上限：宿主误传 min > max 时自动交换。 */
    val sanitizedMaxVideoDurationMs: Long =
        maxOf(minVideoDurationMs, maxVideoDurationMs)

    /** 规整后的图片体积下限（字节）：宿主误传 min > max 时自动交换，负值回退 0。 */
    val sanitizedMinImageSize: Long =
        minOf(minImageSize, maxImageSize).coerceAtLeast(0L)

    /** 规整后的图片体积上限（字节）：宿主误传 min > max 时自动交换。 */
    val sanitizedMaxImageSize: Long =
        maxOf(minImageSize, maxImageSize).coerceAtLeast(0L)

    /** 图片体积过滤是否实际生效（默认 0..Long.MAX_VALUE 视为不过滤，跳过 SQL 子句）。 */
    val hasImageSizeFilter: Boolean
        get() = sanitizedMinImageSize > 0L || sanitizedMaxImageSize != Long.MAX_VALUE

    /**
     * 裁剪是否实际生效：仅"单选 + 纯图片模式"支持裁剪。
     * VIDEO/ALL 模式下视频无法裁剪为静态图，多选下裁剪语义未定义——均静默降级为不裁剪，
     * 避免宿主传入无效组合后进入"裁剪视频卡死"等断裂路径。
     */
    val effectiveCropEnabled: Boolean
        get() = cropConfig.enabled && isSingleSelect && mediaType == MediaType.IMAGE

    /**
     * 相机入口是否实际展示：仅在能显示拍摄产物的模式下生效。
     * 相机拍摄的是静态图（CameraHelper 写入 MediaStore.Images），而 VIDEO 模式列表只查询视频，
     * 拍出的图必然不出现在网格里——此时静默隐藏相机 tile，避免"拍了照却看不到"的断裂路径
     * （与 [effectiveCropEnabled] 一致的无效组合静默降级策略）。
     * IMAGE / ALL 模式下拍摄的静态图可正常显示，相机 tile 照常展示。
     */
    val effectiveShowCamera: Boolean
        get() = showCamera && mediaType != MediaType.VIDEO
}
