# 图片预览长图适配 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 大图预览页对“长图”（偏竖且按高度适配后显示宽度达到屏幕宽度 40% 的图片）改为整宽显示、顶部对齐，并允许未放大时也能单指上下滑动浏览全图；其余图片维持现有 fit-center 展示，任何图片都不裁剪。

**Architecture:** 新增与 Android View 无关的纯函数 `resolveImagePreviewFitMode()`，只依据 view 尺寸与图片原始尺寸返回展示模式枚举，可用 JUnit 直接覆盖长图判定的所有分支；`ZoomableImageView.applyBaseMatrix()` 调用该函数决定用现有 `ScaleToFit.CENTER` 还是新的整宽顶对齐矩阵，另新增 `hasVerticalOverflow()` 辅助判断，把单指上下拖拽平移的触发条件从“已放大”扩展为“已放大或长图内容溢出”。

**Tech Stack:** Kotlin、Android `View`/`Matrix`、JUnit 4、Gradle Android Library（`photo-choice` module）。

---

## Global Constraints

- 设计规范：`docs/superpowers/specs/2026-07-06-image-preview-long-image-fit-design.md`。
- 只改 `photo-choice` 模块的 `ui/preview` 包；不改 `PreviewActivity`/`PreviewPageFragment`/`PreviewImagePageDelegate`/`sample` 模块。
- 正常照片（按宽或按高适配即可完整显示）展示效果必须与改动前完全一致，不允许回归。
- 任何图片在预览页都不能被裁剪。
- 长图判定阈值 `0.4` 保持可配置（函数参数带默认值），不要硬编码在多处。
- 本机 Gradle 构建需要先 `./gradlew --stop`，并附加 `-Porg.gradle.java.installations.paths=E:/Java/jdk-21.0.11`（VS Code redhat.java 自带 JRE 无 `jlink`，直接用会报 `jlink.exe does not exist`）。

---

## File Structure

**Create:**

- `photo-choice/src/main/java/com/google/photochoice/ui/preview/ImagePreviewFitMode.kt` — 长图展示模式枚举 + 纯判定函数。
- `photo-choice/src/test/java/com/google/photochoice/ui/preview/ImagePreviewFitModeTest.kt` — 判定函数单测。

**Modify:**

- `photo-choice/src/main/java/com/google/photochoice/ui/preview/ZoomableImageView.kt` — `applyBaseMatrix()` 按判定结果构造矩阵；新增 `hasVerticalOverflow()`；拖拽相关三处判定条件从 `isZoomed` 扩展为 `isZoomed || hasVerticalOverflow()`；类头注释补充长图行为说明。

## Spec Coverage Map

- 判定规则（4.1）与阈值常量：Task 1。
- 手势联动、顶部对齐、横向切页不受影响（4.2）：Task 2。
- 正常照片/双指缩放/双击缩放行为不变：Task 2（手工验收覆盖）。

---

### Task 1: 长图判定纯函数与单测

**Files:**

- Create: `photo-choice/src/test/java/com/google/photochoice/ui/preview/ImagePreviewFitModeTest.kt`
- Create: `photo-choice/src/main/java/com/google/photochoice/ui/preview/ImagePreviewFitMode.kt`

**Interfaces:**

- Produces: `enum class ImagePreviewFitMode { CENTER, FIT_WIDTH_TOP_ALIGNED }`
- Produces: `const val LONG_IMAGE_WIDTH_RATIO_THRESHOLD: Float`
- Produces: `fun resolveImagePreviewFitMode(viewWidth: Float, viewHeight: Float, imageWidth: Float, imageHeight: Float, longImageWidthRatioThreshold: Float = LONG_IMAGE_WIDTH_RATIO_THRESHOLD): ImagePreviewFitMode`

- [ ] **Step 1: 写出判定函数的失败测试**

创建 `ImagePreviewFitModeTest.kt`：

```kotlin
package com.google.photochoice.ui.preview

import org.junit.Assert.assertEquals
import org.junit.Test

class ImagePreviewFitModeTest {

    @Test
    fun `正常横图按宽高都不溢出时使用居中自适应`() {
        // 1080x1920 屏幕，1200x900 横图：按宽适配(0.9)先于按高适配(2.133)触顶
        assertEquals(
            ImagePreviewFitMode.CENTER,
            resolveImagePreviewFitMode(
                viewWidth = 1080f, viewHeight = 1920f,
                imageWidth = 1200f, imageHeight = 900f
            )
        )
    }

    @Test
    fun `正常竖拍照片按宽适配即可完整显示时使用居中自适应`() {
        // 1080x1920 屏幕，1200x1600 竖拍照片：按宽适配(0.9)先于按高适配(1.2)触顶
        assertEquals(
            ImagePreviewFitMode.CENTER,
            resolveImagePreviewFitMode(
                viewWidth = 1080f, viewHeight = 1920f,
                imageWidth = 1200f, imageHeight = 1600f
            )
        )
    }

    @Test
    fun `明显偏窄的竖图按高适配后宽度占比不足阈值时使用居中自适应`() {
        // 400x3000 窄长条图：按高适配后显示宽度 256px，小于屏宽 40%(432px)
        assertEquals(
            ImagePreviewFitMode.CENTER,
            resolveImagePreviewFitMode(
                viewWidth = 1080f, viewHeight = 1920f,
                imageWidth = 400f, imageHeight = 3000f
            )
        )
    }

    @Test
    fun `长图按高适配后宽度占比达到阈值时整宽顶对齐`() {
        // 1080x3000 长图：按高适配后显示宽度 691.2px，大于屏宽 40%(432px)
        assertEquals(
            ImagePreviewFitMode.FIT_WIDTH_TOP_ALIGNED,
            resolveImagePreviewFitMode(
                viewWidth = 1080f, viewHeight = 1920f,
                imageWidth = 1080f, imageHeight = 3000f
            )
        )
    }

    @Test
    fun `按高适配后宽度恰好等于阈值时判定为长图`() {
        // 1000x2000 屏幕，800x4000 图：按高适配后显示宽度恰好等于屏宽的 40%(400px)
        assertEquals(
            ImagePreviewFitMode.FIT_WIDTH_TOP_ALIGNED,
            resolveImagePreviewFitMode(
                viewWidth = 1000f, viewHeight = 2000f,
                imageWidth = 800f, imageHeight = 4000f
            )
        )
    }

    @Test
    fun `正方形图片使用居中自适应`() {
        assertEquals(
            ImagePreviewFitMode.CENTER,
            resolveImagePreviewFitMode(
                viewWidth = 1080f, viewHeight = 1920f,
                imageWidth = 1000f, imageHeight = 1000f
            )
        )
    }
}
```

- [ ] **Step 2: 运行测试并确认因函数不存在而失败**

Run:

```powershell
./gradlew.bat --stop
./gradlew.bat ":photo-choice:testDebugUnitTest" --tests "com.google.photochoice.ui.preview.ImagePreviewFitModeTest" "-Porg.gradle.java.installations.paths=E:/Java/jdk-21.0.11"
```

Expected: `FAIL`，错误包含 `Unresolved reference 'resolveImagePreviewFitMode'`（或 `ImagePreviewFitMode`）。

- [ ] **Step 3: 实现判定纯函数**

创建 `ImagePreviewFitMode.kt`：

```kotlin
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
 * 长图判定阈值：按高度适配后的显示宽度占屏幕宽度的比例达到该值即视为“长图”。
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
 * 比例达到 [longImageWidthRatioThreshold] 视为微信语义上的“长图”，返回
 * [FIT_WIDTH_TOP_ALIGNED]（整宽显示、顶部对齐、允许上下滑动）；比例过低说明图片本身
 * 明显偏窄，维持 [CENTER] 自适应展示即可。
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
    return if (fitHeightWidth >= viewWidth * longImageWidthRatioThreshold) {
        ImagePreviewFitMode.FIT_WIDTH_TOP_ALIGNED
    } else {
        ImagePreviewFitMode.CENTER
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```powershell
./gradlew.bat ":photo-choice:testDebugUnitTest" --tests "com.google.photochoice.ui.preview.ImagePreviewFitModeTest" "-Porg.gradle.java.installations.paths=E:/Java/jdk-21.0.11" --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`，6 个测试全部通过。

- [ ] **Step 5: 提交判定函数**

```powershell
git add photo-choice/src/main/java/com/google/photochoice/ui/preview/ImagePreviewFitMode.kt photo-choice/src/test/java/com/google/photochoice/ui/preview/ImagePreviewFitModeTest.kt
git commit -m "feat: add long-image fit mode decision for preview"
```

---

### Task 2: 接入 ZoomableImageView 并放开长图拖拽

**Files:**

- Modify: `photo-choice/src/main/java/com/google/photochoice/ui/preview/ZoomableImageView.kt:17-27`（类头注释）
- Modify: `photo-choice/src/main/java/com/google/photochoice/ui/preview/ZoomableImageView.kt:190-203`（`applyBaseMatrix`）
- Modify: `photo-choice/src/main/java/com/google/photochoice/ui/preview/ZoomableImageView.kt:502-569`（触摸事件处理三处判定条件）

**Interfaces:**

- Consumes: Task 1 的 `resolveImagePreviewFitMode()` / `ImagePreviewFitMode`。
- Produces: `ZoomableImageView.hasVerticalOverflow(): Boolean`（private，仅内部使用）。

- [ ] **Step 1: 更新类头注释说明长图行为**

将 `ZoomableImageView.kt` 第 17-27 行的类注释：

```kotlin
/**
 * 大图预览缩放视图。
 *
 * - 初始：fit-center 显示图片
 * - 双指 pinch：1x ~ 3x，越界有阻尼回弹
 * - 双击：1x ↔ 2x 切换
 * - 放大后单指拖拽平移图片，越界有橡皮筋阻尼，松手回弹
 * - 拖到图片水平边界后继续拖拽则交由 ViewPager2 切页
 *
 * 通过 ImageMatrix 实现，不修改 scaleType（外部不能再设 scaleType）。
 */
```

改为：

```kotlin
/**
 * 大图预览缩放视图。
 *
 * - 初始：按 [resolveImagePreviewFitMode] 判定结果展示——正常图片 fit-center 居中；
 *   长图（偏竖且按高适配后显示宽度达到屏宽阈值）整宽显示、顶部对齐
 * - 长图未放大时也可单指上下滑动浏览完整内容，见 [hasVerticalOverflow]
 * - 双指 pinch：1x ~ 3x，越界有阻尼回弹
 * - 双击：1x ↔ 2x 切换
 * - 放大后单指拖拽平移图片，越界有橡皮筋阻尼，松手回弹
 * - 拖到图片水平边界后继续拖拽则交由 ViewPager2 切页
 *
 * 通过 ImageMatrix 实现，不修改 scaleType（外部不能再设 scaleType）。
 */
```

- [ ] **Step 2: 按判定结果构造 baseMatrix**

将 `applyBaseMatrix()`（第 190-203 行）：

```kotlin
    private fun applyBaseMatrix() {
        val d = drawable ?: return
        if (width <= 0 || height <= 0) return
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        if (dw <= 0f || dh <= 0f) return

        val viewRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val drawableRect = RectF(0f, 0f, dw, dh)
        baseMatrix.reset()
        baseMatrix.setRectToRect(drawableRect, viewRect, Matrix.ScaleToFit.CENTER)
        drawMatrix.set(baseMatrix)
        imageMatrix = drawMatrix
    }
```

改为：

```kotlin
    private fun applyBaseMatrix() {
        val d = drawable ?: return
        if (width <= 0 || height <= 0) return
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        if (dw <= 0f || dh <= 0f) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val fitMode = resolveImagePreviewFitMode(viewW, viewH, dw, dh)

        baseMatrix.reset()
        when (fitMode) {
            ImagePreviewFitMode.FIT_WIDTH_TOP_ALIGNED -> {
                // 长图：整宽显示。从原点(0,0)缩放天然顶边+左边对齐，即从图片顶部开始
                // 展示，剩余内容通过 hasVerticalOverflow() 放开的单指上下滑动查看。
                val scale = viewW / dw
                baseMatrix.setScale(scale, scale)
            }
            ImagePreviewFitMode.CENTER -> {
                val viewRect = RectF(0f, 0f, viewW, viewH)
                val drawableRect = RectF(0f, 0f, dw, dh)
                baseMatrix.setRectToRect(drawableRect, viewRect, Matrix.ScaleToFit.CENTER)
            }
        }
        drawMatrix.set(baseMatrix)
        imageMatrix = drawMatrix
    }
```

- [ ] **Step 3: 新增“内容纵向溢出”辅助判断**

在 `currentDisplayRect()` 方法（约第 423-428 行）之后新增：

```kotlin
    /**
     * 当前展示内容的高度是否超出可视区域。
     *
     * 长图整宽模式下即使尚未放大（仍是 1x 的 baseMatrix）也会超出，需要据此放开单指
     * 上下拖拽——否则只有放大状态（[isZoomed]）才允许拖拽，长图无法滑动查看全图。
     */
    private fun hasVerticalOverflow(): Boolean {
        val rect = currentDisplayRect() ?: return false
        return rect.height() > height + 0.5f
    }
```

- [ ] **Step 4: 放开长图的单指上下拖拽判定**

在 `onTouchEvent()` 中，把三处仅依赖 `isZoomed` 的拖拽判定改为同时接受长图纵向溢出：

第一处，`ACTION_DOWN` 分支（约第 515-518 行）：

```kotlin
                if (isZoomed) {
                    // 放大状态：预先阻止 ViewPager2 拦截，优先由本 View 处理拖拽
                    requestDisallowParentIntercept(true)
                }
```

改为：

```kotlin
                if (isZoomed || hasVerticalOverflow()) {
                    // 放大状态或长图纵向溢出：预先阻止 ViewPager2 拦截，优先由本 View 处理拖拽
                    requestDisallowParentIntercept(true)
                }
```

第二处，`ACTION_MOVE` 分支开头（约第 530 行）：

```kotlin
                if (isZoomed && activePointerCount == 1 && !scaleDetector.isInProgress && !isPinching) {
```

改为：

```kotlin
                if ((isZoomed || hasVerticalOverflow()) && activePointerCount == 1 && !scaleDetector.isInProgress && !isPinching) {
```

第三处，`ACTION_UP`/`ACTION_CANCEL` 分支（约第 562-567 行）：

```kotlin
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerCount = event.pointerCount
                if (isDragging && isZoomed) {
                    // 拖拽结束：动画回弹到合法边界
                    snapToBounds(animated = true)
                }
            }
```

改为：

```kotlin
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerCount = event.pointerCount
                if (isDragging && (isZoomed || hasVerticalOverflow())) {
                    // 拖拽结束：动画回弹到合法边界
                    snapToBounds(animated = true)
                }
            }
```

- [ ] **Step 5: 编译校验**

Run:

```powershell
./gradlew.bat ":photo-choice:compileDebugKotlin" "-Porg.gradle.java.installations.paths=E:/Java/jdk-21.0.11"
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 6: 全量单测回归**

Run:

```powershell
./gradlew.bat ":photo-choice:testDebugUnitTest" "-Porg.gradle.java.installations.paths=E:/Java/jdk-21.0.11" --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`，已有测试（含 Task 1 新增的 6 个）全部通过，无回归。

- [ ] **Step 7: 设备/模拟器手工验收**

按顺序在真机或模拟器上执行并记录结果：

1. 打开一张正常照片（横图或普通竖拍），确认展示效果与改动前一致（fit-center 居中），无拉伸、无裁剪。
2. 打开一张明显偏窄的长条竖图（如宽高比 < 1:5 且按屏高适配后宽度小于屏宽 40%），确认仍是居中自适应展示，两侧留白，不做拉伸。
3. 打开一张微信式长图（如聊天记录长截图），确认宽度贴边、从图片顶部开始展示；单指上下滑动可以浏览到图片底部，滑到顶部/底部有回弹，全程无内容被裁剪。
4. 长图状态下，双指缩放、双击缩放正常；缩放后仍可上下左右拖拽平移；双击缩放回 1x 后继续可以上下滑动浏览。
5. 长图页面向左/右滑动切换到相邻图片或视频，确认切页不受“上下滑动查看长图”影响。
6. 依次预览多张不同比例的图片（正常、偏窄竖图、长图混合排列），确认每张图片进入预览时展示模式判定正确、互不影响。

- [ ] **Step 8: 提交接入变更**

```powershell
git add photo-choice/src/main/java/com/google/photochoice/ui/preview/ZoomableImageView.kt
git commit -m "feat: fit long images to width with top-aligned vertical scroll in preview"
```
