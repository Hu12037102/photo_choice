package com.google.photochoice.ui.preview

/**
 * 大图预览的展示模式。
 *
 * - [CENTER]：fit-center，整图完整居中显示（现有默认行为）。
 * - [FIT_WIDTH_TOP_ALIGNED]：整宽显示、顶部对齐，需要上下滑动才能看到全部内容
 *   （微信长图交互）。
 */
internal enum class ImagePreviewFitMode {
    CENTER,
    FIT_WIDTH_TOP_ALIGNED
}

/**
 * 长图判定阈值：按高度适配后的显示宽度占屏幕宽度的比例超过该值即视为“长图”。
 */
internal const val LONG_IMAGE_WIDTH_RATIO_THRESHOLD = 0.4f

/**
 * 根据视图与图片的原始尺寸，判定大图预览应使用的展示模式。
 *
 * 先分别算出“按宽适配”“按高适配”两个缩放系数：谁的结果更小，图片就会先触到对应边、
 * 谁就是 fit-center 的限制条件。若按宽适配是限制条件（正常照片/横图/轻微竖图），
 * 宽度天然贴边且高度必然不超过视图高度，直接维持 [CENTER]。
 *
 * 若按高适配才是限制条件（偏竖的图），再看按高适配后的显示宽度占视图宽度的比例：
 * 比例超过 [longImageWidthRatioThreshold] 视为微信语义上的“长图”，返回
 * [FIT_WIDTH_TOP_ALIGNED]（整宽显示、顶部对齐、允许上下滑动）；比例不超过阈值说明图片
 * 本身明显偏窄，维持 [CENTER] 自适应展示即可。
 *
 * @param viewWidth 预览视图宽度（像素）
 * @param viewHeight 预览视图高度（像素）
 * @param imageWidth 图片原始宽度（像素）
 * @param imageHeight 图片原始高度（像素）
 * @param longImageWidthRatioThreshold 长图判定阈值，默认 [LONG_IMAGE_WIDTH_RATIO_THRESHOLD]
 */
internal fun resolveImagePreviewFitMode(
    viewWidth: Float,
    viewHeight: Float,
    imageWidth: Float,
    imageHeight: Float,
    longImageWidthRatioThreshold: Float = LONG_IMAGE_WIDTH_RATIO_THRESHOLD
): ImagePreviewFitMode {
    val scaleFitWidth = viewWidth / imageWidth
    val scaleFitHeight = viewHeight / imageHeight
    if (scaleFitWidth <= scaleFitHeight) {
        return ImagePreviewFitMode.CENTER
    }
    val fitHeightWidth = imageWidth * scaleFitHeight
    return if (fitHeightWidth > viewWidth * longImageWidthRatioThreshold) {
        ImagePreviewFitMode.FIT_WIDTH_TOP_ALIGNED
    } else {
        ImagePreviewFitMode.CENTER
    }
}
