# 裁剪页 Activity 化 & 单选模式隐藏 checkbox

日期：2026-05-24
范围：`:photo-choice` 库模块

## 背景

当前实现存在两个问题：

1. **裁剪页与选择器共用 Activity**：`CropFragment` 由 `PhotoChoiceActivity` 通过 `FragmentTransaction` 承载，进入/退出裁剪时 `PhotoChoiceActivity` 需要切换 toolbar、bottom bar、date header 的可见性，再通过 `setFragmentResultListener` 收回裁剪结果。`PhotoChoiceActivity` 同时承担"网格容器"与"裁剪容器"两个职责，状态耦合多、可维护性差。`PreviewActivity` 已经独立，但裁剪没跟上。

2. **单选模式下网格仍显示 checkbox**：当 `maxSelectCount == 1` 时，每个 item 上的方形选中框依然渲染，与单选语义不符（单选模式下点击 item 即完成选择，不需要 checkbox 显式标记）。

## 目标

- 将裁剪页改为独立 `CropActivity` 承载，与 `PreviewActivity` 风格一致；`PhotoChoiceActivity` 不再持有裁剪相关状态与切换逻辑。
- 当 `maxSelectCount == 1` 时（与是否启用裁剪无关），隐藏网格 item 上的 checkbox、序号文本、禁用蒙层与 checkbox 点击热区。

## 非目标

- 不改变单选模式下点击 item 的行为（保持现状：单选+裁剪 → 进裁剪页；单选无裁剪 → 现有 fall-through 路径）。
- 不修改预览页、底部栏、压缩流程。
- 不改 `SelectMode` 字段与其语义；单选判定统一使用 `config.maxSelectCount == 1`，与 `SelectMode.SINGLE` 不挂钩。

## 设计

### 改动 1：裁剪页独立 Activity 承载

#### 新增 `CropActivity`

文件：`photo-choice/src/main/java/com/google/photochoice/ui/crop/CropActivity.kt`

职责：
- 通过 `Intent` extra 接收源 URI 与初始裁剪比例名（`EXTRA_SOURCE_URI`、`EXTRA_INITIAL_RATIO`）。
- 内部 `FragmentContainerView` 承载 `CropFragment`。
- 通过 `supportFragmentManager.setFragmentResultListener` 监听 `CropFragment` 的裁剪结果：
  - 收到 URI → `setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT_URI, uri))` + `finish()`。
- 用户取消（顶栏返回 / 系统返回键）→ `setResult(RESULT_CANCELED)` + `finish()`。
- edge-to-edge 与 system bar 处理参照 `PreviewActivity`。

对外契约：
```kotlin
companion object {
    const val EXTRA_SOURCE_URI = "source_uri"
    const val EXTRA_INITIAL_RATIO = "initial_ratio"   // CropAspectRatio.name
    const val EXTRA_RESULT_URI = "result_uri"

    fun intent(context: Context, sourceUri: String, initialRatio: CropAspectRatio): Intent
}
```

#### 修改 `CropFragment`

- 移除 `viewModels(ownerProducer = { requireActivity() })`，不再依赖 `PhotoChoiceViewModel`。
- 初始比例改由 `arguments` 传入：`ARG_INITIAL_RATIO`（`CropAspectRatio.name`，默认 `ORIGINAL`）。
- `btnCancel` 点击：`requireActivity().finish()`。
- `performCrop()` 成功后仍通过 `parentFragmentManager.setFragmentResult(REQUEST_KEY, ...)` 回传 URI（由 `CropActivity` 监听）。
- `newInstance(uri: String)` 签名扩展为 `newInstance(uri: String, initialRatio: CropAspectRatio)`。

#### Manifest 与主题

`photo-choice/src/main/AndroidManifest.xml` 新增：
```xml
<activity
    android:name=".ui.crop.CropActivity"
    android:exported="false"
    android:theme="@style/Theme.PhotoChoice.Crop" />
```

`themes.xml` 新增 `Theme.PhotoChoice.Crop`，参照已有的 `Theme.PhotoChoice.Preview`（黑底全屏风格 — 裁剪页背景为黑色，与现有 fragment_crop 布局背景一致）。

#### `MediaGridFragment` 启动裁剪

- 新增 `cropLauncher: ActivityResultLauncher<Intent>`（`StartActivityForResult` 契约）。
- 单选 + 裁剪路径 ([MediaGridFragment.kt:245-247](photo-choice/src/main/java/com/google/photochoice/ui/grid/MediaGridFragment.kt#L245-L247))：
  - 保留 `viewModel.selectionManager.select(mediaFile)`。
  - 改 `viewModel.navigateToCrop(mediaFile.uri)` 为：
    ```kotlin
    cropLauncher.launch(
        CropActivity.intent(
            requireContext(),
            mediaFile.uri,
            config.cropConfig.aspectRatio
        )
    )
    ```
- `cropLauncher` 回调：
  - `RESULT_OK` → 读取 `EXTRA_RESULT_URI`，调用宿主 `(requireActivity() as PhotoChoiceActivity).finishWithCropResult(uri)`。
  - `RESULT_CANCELED` → 不做特殊处理（用户在裁剪页取消，回到网格，已选中的 item 保持 SelectionManager 中的选中态；多选时通过 toggle 处理，单选下下一次点击其他 item 会替换）。

#### `PhotoChoiceActivity` 简化

删除：
- `enterCrop(uri: String)`、`exitCrop()`、`TAG_CROP` 常量。
- `observeState()` 内 `viewModel.showCrop` 的 collect。
- `observeState()` 内 `supportFragmentManager.setFragmentResultListener(CropFragment.REQUEST_KEY, ...)`。
- `setupBackPress()` 内 `viewModel.showCrop.value != null → viewModel.dismissCrop()` 分支。

新增 `fun finishWithCropResult(croppedUri: String)`：
- 复用现有 `pendingResultCallback` + `resultDelivered` 流程，把 `croppedUri` 包装为 `PhotoChoiceResult(uris=[uri], paths=[uri.path])`，回调宿主后 `finish()`。
- 逻辑复制自现有 `setFragmentResultListener` 内的回调代码块。

#### `PhotoChoiceViewModel` 清理

删除：
- `_showCrop` / `showCrop` StateFlow。
- `navigateToCrop(uri: String)` 与 `dismissCrop()` 方法。

### 改动 2：单选模式隐藏网格 checkbox

#### `MediaGridAdapter` 修改

- 构造函数新增参数 `isSingleSelect: Boolean`。
- `MediaVH.bind(mediaItem)` 内：
  - 若 `isSingleSelect == true`：
    - `checkbox.visibility = View.GONE`
    - `tvOrder.visibility = View.GONE`
    - `disabledOverlay.visibility = View.GONE`
    - `touchTarget.visibility = View.GONE`（同时禁掉 checkbox 点击热区，避免遮挡 itemView 点击）
    - 跳过 `bindSelectionState(mediaItem)` 调用。
  - 否则维持原逻辑。
- `bindSelectionState(mediaItem)` 与 payload 路径在单选模式下不会被触发（`isSingleSelect` 由外部传入，行为确定），无需额外防御。

#### `MediaGridFragment` 修改

创建 `MediaGridAdapter` 时传入：
```kotlin
isSingleSelect = config.maxSelectCount == 1
```

底部栏与选中态的现有逻辑保持不变 —— 单选模式下 `SelectionManager` 仍按 `maxSelectCount == 1` 维护至多 1 个选中项，`MediaGridFragment` 既有的 `notifyAllSelectionChanged` 等通知在单选下因 `bind` 跳过了 `bindSelectionState` 也不会渲染出选中态。

## 验证

构建验证：
- `./gradlew :photo-choice:assembleDebug :sample:assembleDebug`
- `./gradlew lint` 确保无 @Deprecated API 引入。

功能验证（在 sample app 中手动确认）：
1. **多选 + 裁剪关闭**：选择 3 张图 → 点击完成 → 返回宿主。Checkbox 正常显示。
2. **单选 + 裁剪开启**：点击 item → 启动 `CropActivity` → 裁剪完成 → 返回宿主。网格 checkbox 全部隐藏。
3. **单选 + 裁剪关闭**：点击 item → 走现有 fall-through 路径（不在本次改动范围）。网格 checkbox 全部隐藏。
4. **裁剪取消**：进入 `CropActivity` 后按返回 → 回到网格，无异常。
5. **裁剪页旋转**：进入 `CropActivity` 后旋转设备 → 状态保留或合理重建（CropFragment 通过 args 重建）。
6. **多任务回退**：裁剪页在前台时切到后台再切回 → 状态正常。
7. **PreviewActivity 与 CropActivity 互不影响**：先进预览 → 退出 → 单选裁剪路径正常启动。

## 风险与回滚

- 风险：`PhotoChoiceViewModel.showCrop` 被删除后，若有遗漏的引用会编译失败 —— 借由编译器即可发现。
- 风险：`CropFragment` 不再注入 `PhotoChoiceViewModel`，但 `config.cropConfig.aspectRatio` 现在通过 args 传入 —— 需要确保 `CropActivity.intent()` 调用方传入正确比例。
- 回滚：本次改动局限于裁剪相关文件 + `MediaGridAdapter` 一个布尔参数，git revert 即可。
