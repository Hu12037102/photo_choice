# CLAUDE.md

本文件为 Claude Code（claude.ai/code）在此仓库中工作时提供指导。

## 项目概览

PhotoChoice 是一个 Android 相册选择器组件，目标是对标微信相册选择体验。当前处于早期脚手架阶段——仅有 Android Studio 模板生成的样板代码。完整产品规格见 [PRD.md](PRD.md)。

- **包名**：`com.google.photochoice`
- **最低 SDK**：29（Android 10，利用 Scoped Storage 无需存储权限）
- **目标 SDK**：36
- **语言**：Kotlin
- **硬性约束**：零过时 API，`@Deprecated` 标注的方法/类一律不得使用
- **许可证**：Apache 2.0

## 构建命令

```bash
# 构建库与演示应用
./gradlew :photo-choice:assembleDebug :sample:assembleDebug

# 运行单元测试（JVM）
./gradlew test

# 运行单个单元测试类
./gradlew test --tests "com.google.photochoice.ExampleUnitTest"

# 运行仪器化测试（需连接设备或模拟器）
./gradlew connectedAndroidTest

# Lint 检查（含过时 API 检测）
./gradlew lint

# 清理构建
./gradlew clean
```

## 架构（规划中，来自 PRD）

组件采用 MVVM + 分层架构：

```
API 层 (PhotoChoice.kt)         — Builder 模式对外唯一入口
  └─ UI 层 (Fragment/Activity)
       └─ ViewModel 层           — 状态管理，核心是 SelectionManager
            └─ Data 层 (Repository)
                 └─ System 层 (MediaStore via ContentResolver)
```

**核心规划组件：**
- `SelectionManager` — 选中状态管理中枢，维护已选媒体集合，强制 min/max 数量和类型过滤
- `MediaRepository` + `AlbumRepository` — 查询 MediaStore，返回领域模型（相册封面取每个目录 `DATE_ADDED` 降序首条）
- `MediaPagingSource` — Paging 3 数据源，分页查询 MediaStore（每页约 100 条）
- 网格使用 `RecyclerView` + `ListAdapter` + `DiffUtil`，`grid_spacing` 4dp（左右 = 上下）

**关键 UI 交互：**
- **标题栏**：返回箭头(左) + 目录名居中(可点击)。有媒体文件时展示目录名 ▾；无媒体时不展示，无下拉入口
- **相册下拉面板**：点击居中目录名 → 面板从标题栏下方平移展开（Ease-Out 250ms）。高度自适应：内容 ≤ 屏幕 2/3 → wrap_content；> 2/3 → 固定 2/3 + 滚动
- **选中框**：方形（2dp 微圆角），选中时 accent 色填充 + 白色数字，未选中时灰色线框描边
- 图片加载使用 Glide；视频播放使用 ExoPlayer（可选依赖）

## 当前状态

项目为 **`:photo-choice` 库模块** + **`:sample` 演示应用**。依赖通过 Gradle 版本目录 `gradle/libs.versions.toml` 管理。

**已实现（概览）：**
- 媒体网格（Paging 3）、相册切换、多选/单选、底部选中栏
- 大图预览（缩放、全屏 chrome、视频播放、实况图长按播放）
- 单选裁剪、可选压缩、相机入口、多语言

**待建设内容（按 PRD 里程碑）：**
1. M1 — 基础框架：工程搭建、模块划分、数据层、权限、多语言
2. M2 — 核心选择：媒体网格、多选/单选、SelectionManager
3. M3 — 相册切换：相册列表、相册切换、底部选中栏
4. M4 — 预览：大图预览、缩放、拖拽关闭、视频播放
5. M5 — 高级功能：相机入口、裁剪、压缩
6. M6 — 打磨：动画、暗色模式、边缘场景、清理

## 设计决策（来自 PRD）

- 数据源仅限 MediaStore 公共媒体——不加载私有/隐藏目录
- 视频时长上限默认 60 秒（对标微信朋友圈），超长视频不出现在可选列表中
- 裁剪仅在单选图片模式下可用
- 压缩策略：先尺寸压缩（等比缩放至最大宽高边界内），再 JPEG 质量压缩；输出到 `cacheDir/photo_choice/`，超过 24 小时自动清理
- 主题：仅开放 Light / Dark / FollowSystem 三档，不暴露自定义主题色
- 视觉风格：扁平极简单色（黑白灰），零阴影、锐利直角（缩略图 0dp），选中框方形 2dp 微圆角，线框图标优先
- 网格间距：`grid_spacing` 4dp，左右与上下统一
- 相册封面：每个相册取该目录 `DATE_ADDED` 降序首条作为封面缩略图
- 零过时 API：所有实现不得使用 `@Deprecated` 标注的类/方法，必须使用替代 API

## 依赖项

- **Glide** — 图片加载（成熟稳定、链式 API，内置 LRU 内存缓存 + 磁盘缓存 + BitmapPool）
- **Paging 3** — 分页加载，首屏秒开，滚动按需取下一页
- **ExoPlayer (Media3)** — 可选依赖，用于预览页视频播放
- **ViewPager2** — 预览页左右滑动切换
- **ViewBinding** — 替代 `findViewById`，减少 measure/layout 开销
