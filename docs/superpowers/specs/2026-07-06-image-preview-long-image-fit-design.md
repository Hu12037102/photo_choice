# 图片预览长图适配设计

**日期：** 2026-07-06
**状态：** 已确认
**范围：** 大图预览页（`ZoomableImageView`）中静态图片/动图的初始展示与手势滑动，对齐微信长图预览交互

## 1. 背景

预览页当前对所有图片统一使用 `ScaleToFit.CENTER` 做 fit-center：整图必须完整可见、居中显示，绝不裁剪。这对正常比例的照片和明显偏窄的竖图（长条状截图）没有问题，但对“偏竖但不算极端窄”的长图（例如常见的聊天记录截图、长网页截图）会导致图片被整体缩得很小、两侧留白过多，细节难以辨认，且用户无法像微信那样直接上下滑动查看整张长图的各个部分。

## 2. 目标

- 明显偏窄的竖图维持现有自适应（fit-center）展示，不做改动。
- 偏竖但达到一定宽度占比的长图，改为宽度贴边显示，允许通过上下滑动浏览完整图片内容，且从图片顶部开始展示。
- 任何图片在预览页中都不能被裁剪——需要滑动查看的部分只是暂时不在可视区域内，不是被裁掉。
- 双指缩放、双击缩放等现有手势行为保持不变。
- 横向切页（`ViewPager2`）行为不受影响。

## 3. 非目标

- 不改变正常照片（宽度或高度适配后即可完整显示、无需滚动的图片）的展示方式。
- 不改变实况照片播放、长按播放等现有交互。
- 不引入新的手势类型（如专门的“长图模式”开关 UI）。

## 4. 业务行为

### 4.1 判定规则

设屏幕（即 `ZoomableImageView` 自身）宽高为 `viewW × viewH`，图片原始宽高为 `dw × dh`：

- `scaleFitWidth = viewW / dw`，`scaleFitHeight = viewH / dh`。
- 若 `scaleFitWidth <= scaleFitHeight`（图片按宽度适配即可完整显示，正常照片/横图/轻微竖图）：维持 `CENTER` 自适应展示，不做改动。
- 若 `scaleFitHeight < scaleFitWidth`（图片是竖图，需要按高度适配才能完整显示）：
  - 令 `fitHeightWidth = dw * scaleFitHeight`（按高度适配后，图片实际显示的宽度）。
  - `fitHeightWidth <= viewW * 0.4`：判定为明显偏窄的竖图，维持 `CENTER` 自适应展示。
  - `fitHeightWidth > viewW * 0.4`：判定为“长图”，改为按宽度适配（`scale = scaleFitWidth`），图片顶边与视图顶边对齐（不做垂直居中），即从图片顶部开始展示。

阈值 `0.4` 作为常量维护，语义为“按高度适配后的显示宽度占屏幕宽度的比例”。

### 4.2 手势

- 长图在整宽模式下，高度必然超出可视区域（否则不会进入该分支）。即使当前缩放倍数仍是初始的 1x（未双指/双击放大），也需要允许单指上下拖动浏览剩余内容。
- 横向：长图整宽模式下宽度精确贴边，没有横向可拖余量，单指横向拖拽会照常被判定为“已到水平边界”，从而把触摸事件让给 `ViewPager2` 处理左右切页，与现有放大后拖拽到边界切页的逻辑一致。
- 双指缩放 / 双击缩放：在新的 base 矩阵基础上继续 1x ~ 3x 缩放，行为不变。

### 4.3 越界拖拽阻尼修复

长图未放大也可拖拽后，暴露出既有越界阻尼实现（`applyOverscrollResistance()`）的一个缺陷：它按“当前已越界位移”的固定比例每帧拉回一部分，越界量会在几帧内收敛到一个和继续拖拽距离无关的极小定值，视觉上表现为“拖到顶部/底部后拖不动”，松手也几乎看不出回弹动画。该缺陷对垂直方向（长图与已放大图片的上下拖拽）始终存在，只是长图新增了不放大也可拖拽的场景后更容易被感知到；水平方向因为一到边界就直接把触摸交给 `ViewPager2` 切页，几乎不会触发到这个问题。

修复为“越界越多、本帧新增位移的通过比例越小、且越界量随持续拖拽渐进逼近一个明确上限”的阻尼模型（与已有的 `applyScaleDamping()` 缩放越界阻尼同一思路），使拖拽和松手回弹在长图与已放大图片上表现一致。

### 4.4 越界量计算的方向性修复

上一版“越界量”计算（`horizontalOvershoot`/`verticalOvershoot`）在“向上/向左拖拽”分支写反了符号：用 `contentEnd - viewSize` 而非 `viewSize - contentEnd`。对长图而言，向上滑动查看正文（内容底边 `contentEnd` 远大于可视区域 `viewSize`，属于合法滚动）会被误判成一个巨大的“越界量”，导致阻尼几乎把增量完全衰减为 0——表现为“长图完全无法向上滑动”，比 4.3 节的收敛问题更严重。已改为独立纯函数 `computeOvershoot(contentStart, contentEnd, viewSize, delta)`：只有内容终点已经拖过可视区域下边界（`contentEnd < viewSize`）才计入越界，其余（含长图正常滚动区间）返回 0。

## 5. 技术方案

- 新增纯函数（不依赖 Android `View`/`Matrix`），根据 `viewW/viewH/dw/dh` 与阈值常量返回展示模式（`CENTER` 或 `FIT_WIDTH_TOP_ALIGNED`），置于 `ui/preview` 包内，便于 JUnit 覆盖长图、偏窄竖图、正常照片、临界比例等场景。
- `ZoomableImageView.applyBaseMatrix()` 调用该纯函数决定分支，`FIT_WIDTH_TOP_ALIGNED` 分支下用 `Matrix.setScale(scaleFitWidth, scaleFitWidth)`（从原点缩放，天然顶边+左边对齐，无需额外位移）构造 `baseMatrix`；`CENTER` 分支维持现有 `setRectToRect(..., ScaleToFit.CENTER)`。
- 新增“内容高度是否超出可视区域”的辅助判断，用于放宽单指拖拽平移的触发条件：由原来的仅 `isZoomed` 扩展为 `isZoomed || 内容高度超出可视区域`。该辅助判断只读当前 `displayRect` 与 `view` 高度比较，不引入新状态。
- 不改变 `isZoomed` 语义（仍表示“相对 base 矩阵是否放大”），不影响双击缩放阈值判断、`onZoomStateChanged` 回调等既有逻辑。
- 新增纯函数 `dampOverscrollDelta(delta, overshoot, maxOvershoot)` 替换 `applyOverscrollResistance()`：拖拽前先算出本帧位移施加前、沿拖拽方向已经越界的位移量，据此对本帧增量做渐进阻尼后再一次性平移，越界量渐近逼近 `view` 对应边长的固定比例（`MAX_OVERSCROLL_DRAG_RATIO`），不再出现收敛到极小定值的问题。
- 新增纯函数 `computeOvershoot(contentStart, contentEnd, viewSize, delta)` 统一计算某一轴上的越界量，`horizontalOvershoot`/`verticalOvershoot` 委托给它，避免两处手写符号不一致导致的方向性错误。

## 6. 测试与验收

### 6.1 单元测试

- 正常照片（宽度或高度适配都不溢出）→ `CENTER`。
- 明显偏窄竖图（按高度适配后宽度 ≤ 0.4 屏宽）→ `CENTER`。
- 长图（按高度适配后宽度 > 0.4 屏宽）→ `FIT_WIDTH_TOP_ALIGNED`。
- 临界比例（恰好等于 0.4 屏宽）→ `CENTER`（按“大于”口径，等于不算长图）。
- 正方形图片 / 宽高相等的边界情况 → `CENTER`。
- `dampOverscrollDelta`：未越界原样通过、越界达到上限时完全衰减为 0、越界为上限一半时衰减一半、越界超过上限不反向、未设上限不阻尼；并用连续多帧模拟验证持续拖拽时越界量会渐进增长而非收敛到极小定值。
- `computeOvershoot`：内容未溢出可视区域不算越界；向下/向右拖拽在合法范围内（起点未越过 0）不算越界，越过起点边界才计入；向上/向左拖拽在合法范围内（终点未越过可视区域，覆盖长图正常滚动区间）不算越界，越过终点边界才计入；增量为 0 时不算越界。

### 6.2 手工 UI 验收

- 正常照片：展示与现有效果一致，无回归。
- 偏窄长条竖图：居中自适应展示，不做拉伸。
- 微信式长图（如长截图）：宽度贴边、从顶部开始展示，单指向上滑动能顺畅浏览到底部内容（不会中途卡住/划不动），滑动到底部/顶部有回弹，不会露出裁剪或空白之外的异常内容。
- 长图滑动到顶部或底部后继续拖拽，应有和已放大图片一致的橡皮筋阻尼手感（越拖阻力越大但不会拖不动），松手后能看到明显的回弹动画。
- 长图页面左右滑动切换到相邻图片/视频，不受滑动查看长图的影响。
- 长图状态下双指缩放、双击缩放正常，缩放后仍可上下左右拖拽平移，缩放回 1x 后继续保持可上下滑动浏览。

## 7. 影响范围

新增/修改均在 `photo-choice/src/main/java/com/google/photochoice/ui/preview/` 包内：新增 `ImagePreviewFitMode.kt`、`OverscrollDamping.kt` 两个纯函数文件及对应单元测试；修改 `ZoomableImageView.kt`（`applyBaseMatrix`、拖拽判定条件、越界拖拽阻尼实现）。不改动 `PreviewActivity`/`PreviewPageFragment`/`PreviewImagePageDelegate` 等外层文件。
