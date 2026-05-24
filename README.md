# PhotoChoice

Android 相册选择器组件，交互与视觉对标微信朋友圈相册：网格多选、相册切换、大图预览、拍照入口、单图裁剪与可选压缩。通过 **Builder 链式 API** 接入，无需直接启动内部 Activity。

- **包名**：`com.google.photochoice`
- **最低 SDK**：29（Android 10，Scoped Storage，无需写存储权限即可读公共媒体）
- **目标 SDK**：36
- **语言**：Kotlin
- **许可证**：[Apache License 2.0](LICENSE)

---

## 功能概览

| 能力 | 说明 |
|------|------|
| 媒体类型 | 仅图片 / 仅视频 / 图片+视频 |
| 选择模式 | 单选 / 多选，可配置最少/最多数量 |
| 相册 | 按 MediaStore 目录聚合，可切换相册 |
| 网格 | 可配置列数（2–6），正方形缩略图，分页加载 |
| 拍照 | 可选首格相机入口（写入系统相册） |
| 预览 | 可选大图预览、左右滑动 |
| 裁剪 | 单选 + 图片模式下可启用裁剪 |
| 压缩 | 可选完成时 JPEG 尺寸+质量压缩 |
| 主题 | 浅色 / 深色 / 跟随系统 |

---

## 快速开始

### 1. 引入模块

在宿主工程的 `settings.gradle.kts` 中纳入本仓库（或拷贝 `photo-choice` 模块），例如：

```kotlin
include(":photo-choice")
```

在 **Application 或业务模块** 的 `build.gradle.kts` 中：

```kotlin
dependencies {
    implementation(project(":photo-choice"))
}
```

> 当前以 **源码模块** 方式集成；若你方已发布 Maven 坐标，将 `implementation(project(":photo-choice"))` 替换为对应依赖即可。

### 2. 声明权限

库模块已在 `photo-choice` 的 Manifest 中声明读取媒体权限；**宿主 App 仍需在自身 Manifest 中声明相同权限**，并在运行时向用户申请。

| Android 版本 | 权限 |
|--------------|------|
| API 33+ | `READ_MEDIA_IMAGES`、`READ_MEDIA_VIDEO`（按 `mediaType` 实际需要申请） |
| API 29–32 | `READ_EXTERNAL_STORAGE` |

可使用库提供的 `PermissionHelper` 获取权限列表与是否已授权：

```kotlin
import com.google.photochoice.util.PermissionHelper

if (PermissionHelper.hasMediaPermission(context)) {
    openPhotoChoice()
} else {
    requestPermissionLauncher.launch(PermissionHelper.requiredMediaPermissions())
}
```

参考实现见仓库 **`sample`** 模块中的 `MainActivity`。

### 3. 启动选择器

在 **`FragmentActivity`**（或 `AppCompatActivity`）中调用：

```kotlin
import com.google.photochoice.PhotoChoice
import com.google.photochoice.config.MediaType
import com.google.photochoice.config.SelectMode

PhotoChoice.with(this)
    .maxSelectCount(9)
    .minSelectCount(1)
    .selectMode(SelectMode.MULTI)
    .mediaType(MediaType.IMAGE)
    .spanCount(4)
    .showCamera(true)
    .forResult(this) { result ->
        if (result == null) {
            // 用户取消（返回键 / 未点完成即退出）
            return@forResult
        }
        result.uris.forEach { uri ->
            // 使用 content:// 或 file:// URI
        }
    }
```

**注意：**

- 入口类为 `PhotoChoice`，不要直接 `startActivity` 打开 `PhotoChoiceActivity`。
- 取消选择时回调 **`null`**；成功完成时回调 **`PhotoChoiceResult`**。
- `forResult` 会立即启动选择页，请确保已具备媒体读取权限（或先申请再调用）。

---

## 返回结果

```kotlin
data class PhotoChoiceResult(
    val uris: List<Uri>,   // 选中媒体的 URI 列表（顺序与选择顺序一致）
    val paths: List<String> // 尽力解析的本地路径；无法解析时为 URI 字符串
)
```

- 未开启压缩时，一般为 **MediaStore `content://` URI**。
- 开启 **压缩** 或 **裁剪** 后，可能为 **`cacheDir/photo_choice/`** 下的 `file://` URI。
- 使用完毕后若不再需要缓存文件，可调用：

```kotlin
PhotoChoice.cleanup(context)
```

会清理组件沙盒目录中超过 24 小时的临时文件（亦可在业务处理完图片后主动调用）。

---

## 配置项（Builder API）

| 方法 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `maxSelectCount` | `Int` | `9` | 最多可选数量，≥ 1 |
| `minSelectCount` | `Int` | `1` | 至少选择数量才能点「完成」，范围 `[1, max]` |
| `selectMode` | `SelectMode` | `MULTI` | `SINGLE` / `MULTI` |
| `mediaType` | `MediaType` | `IMAGE` | `IMAGE` / `VIDEO` / `ALL` |
| `spanCount` | `Int` | `3` | 网格列数，**2–6** |
| `showCamera` | `Boolean` | `true` | 是否在网格首格显示拍照入口 |
| `minVideoDuration` | `Long` | `0` | 视频最短时长（毫秒），仅筛选视频时有效 |
| `maxVideoDuration` | `Long` | `60000` | 视频最长时长（毫秒），超长不出现在列表 |
| `themeMode` | `ThemeMode` | `FOLLOW_SYSTEM` | `LIGHT` / `DARK` / `FOLLOW_SYSTEM` |
| `cropConfig` | `CropConfig` | 见下 | 裁剪配置 |
| `compressConfig` | `CompressConfig` | 见下 | 完成时压缩配置 |

也可分步构建：

```kotlin
val photoChoice = PhotoChoice.with(context)
    .maxSelectCount(1)
    .build()
photoChoice.forResult(activity) { result -> /* ... */ }
```

### 裁剪 `CropConfig`

仅在 **`selectMode = SINGLE`** 且 **`mediaType` 含图片** 时，用户选图后会进入裁剪页。

```kotlin
import com.google.photochoice.config.CropConfig
import com.google.photochoice.config.CropAspectRatio

.cropConfig(
    CropConfig(
        enabled = true,
        aspectRatio = CropAspectRatio.SQUARE, // ORIGINAL, SQUARE, RATIO_3_4, RATIO_4_3, RATIO_9_16, RATIO_16_9
        maxWidth = 0,   // 0 表示不限制输出宽
        maxHeight = 0
    )
)
```

单选且启用裁剪时，选图即走裁剪流程，完成后直接回调结果并关闭选择器。

### 压缩 `CompressConfig`

在用户点击「完成」后、回调前对**图片**做等比缩放 + JPEG 压缩；视频不压缩。

```kotlin
import com.google.photochoice.config.CompressConfig

.compressConfig(
    CompressConfig(
        enabled = true,
        maxWidth = 1920,
        maxHeight = 1920,
        quality = 80
    )
)
```

---

## 常见场景示例

### 朋友圈多图（最多 9 张图）

```kotlin
PhotoChoice.with(activity)
    .maxSelectCount(9)
    .minSelectCount(1)
    .mediaType(MediaType.IMAGE)
    .selectMode(SelectMode.MULTI)
    .spanCount(4)
    .showCamera(true)
    .forResult(activity) { result -> /* ... */ }
```

### 头像（单选 + 正方形裁剪）

```kotlin
PhotoChoice.with(activity)
    .maxSelectCount(1)
    .minSelectCount(1)
    .selectMode(SelectMode.SINGLE)
    .mediaType(MediaType.IMAGE)
    .cropConfig(CropConfig(enabled = true, aspectRatio = CropAspectRatio.SQUARE))
    .compressConfig(CompressConfig(enabled = true))
    .forResult(activity) { result -> /* ... */ }
```

### 仅选视频（最长 60 秒）

```kotlin
PhotoChoice.with(activity)
    .maxSelectCount(1)
    .mediaType(MediaType.VIDEO)
    .showCamera(false)
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }
```

### 图片 + 视频混合

```kotlin
PhotoChoice.with(activity)
    .maxSelectCount(9)
    .mediaType(MediaType.ALL)
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }
```

---

## Demo 工程

仓库包含 **`sample`** 应用模块，用于演示全部配置项与快捷场景：

```bash
./gradlew :sample:installDebug
```

运行 **PhotoChoice 示例** App：可调整参数后打开选择器，并在结果区点击缩略图预览已选媒体。

---

## 工程结构

```
photo_choice/
├── photo-choice/     # 库模块（对外 API：PhotoChoice）
├── sample/           # 示例 App
├── PRD.md            # 产品规格（内部参考）
└── README.md         # 本文档
```

---

## 构建与校验

```bash
# 编译库
./gradlew :photo-choice:assembleDebug

# 编译并安装示例
./gradlew :sample:installDebug

# 单元测试
./gradlew test

# Lint
./gradlew lint
```

---

## 接入检查清单

- [ ] 已 `implementation(project(":photo-choice"))`（或等价 Maven 依赖）
- [ ] 宿主 Manifest 已声明媒体读取权限
- [ ] 启动前已申请并获得权限（可参考 `PermissionHelper`）
- [ ] 在 `FragmentActivity` 上调用 `PhotoChoice.with(...).forResult(...)`
- [ ] 正确处理 `result == null`（取消）与 `PhotoChoiceResult`（成功）
- [ ] 若启用压缩/裁剪，按需在处理完成后调用 `PhotoChoice.cleanup(context)`

---

## 限制与说明

- 数据源为 **MediaStore 公共媒体**，不包含私有/隐藏目录。
- 组件 UI 与主题色不对外开放自定义，仅支持 `ThemeMode` 三档。
- 内部 Activity（`PhotoChoiceActivity`、`PreviewActivity`）**不应**由业务方直接启动。
- 视频时长过滤仅影响列表展示，不改变系统相册中的原始文件。

---

## 问题反馈

请在仓库 Issue 中说明：**Android 版本、配置代码片段、期望与实际行为**。复现步骤越完整越便于排查。
