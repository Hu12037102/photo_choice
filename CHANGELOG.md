# Changelog

本文件记录 PhotoChoice 的所有重要变更。

版本遵循 [语义化版本](https://semver.org/lang/zh-CN/)（`主版本.次版本.修订号`）。

---

## [1.0.0] - 2026-07-28

首个正式发布版本。面向宿主 App 提供开箱即用的 Android 相册选择器组件，通过 Builder API + `PhotoChoiceContract` 接入，无需直接启动内部 Activity。

### 功能

- **媒体选择**：仅图片 / 仅视频 / 图片+视频三种模式；单选或多选（`selectCount` 1–9）。
- **相册切换**：按 MediaStore 目录聚合，下拉切换相册。
- **网格**：可配置列数（2–6），正方形缩略图，基于 Paging 3 的 keyset 分页加载，`load` 不做 XMP 解析，冷启动与快滑流畅。
- **滚动日期条**：滚动时显示当前可见区域日期，悬浮胶囊从右侧滑入 / 滑出。
- **拍照入口**：可选首格相机 tile，拍摄结果写入系统相册。
- **大图预览**：全屏左右滑动，视频内嵌播放。
- **实况图 / Motion Photo**：识别 Motion Photo / Google Motion Photo / Samsung 动态照片，网格 LIVE 角标 + 预览长按播放内嵌短视频；检测结果常驻索引，跨配置变更与进程死亡持久保留。
- **单图裁剪**：单选 + 图片模式下进入独立 `CropActivity`，支持多种宽高比；可选输出尺寸上限（`CropConfig.maxWidth/maxHeight` 等比缩放）。
- **压缩**：完成时对图片做等比缩放 + JPEG 质量压缩（默认最长边 1280、起始质量 80、体积上限约 1.5MB 自适应递减）；视频、GIF、保留动效的实况图不压缩。
- **主题**：浅色 / 深色 / 跟随系统，per-Activity 生效，不改写宿主全局。
- **双轨启动 API**：推荐 `PhotoChoiceContract`（无静态状态，抗 Activity 重建与进程死亡）；兼容 `forResult` 回调（旧轨）。

### 稳定性与正确性

- **配置防御性规整**：`selectCount`、`spanCount`、视频时长、图片体积等越界入参一律回退修正而非抛异常，宿主传参失误不导致 Crash。
- **无效组合静默降级**：视频 / 多选模式下裁剪自动关闭（`effectiveCropEnabled`）；VIDEO 模式下相机 tile 自动隐藏（`effectiveShowCamera`），避免"拍了照却看不到"的断裂路径。
- **进程死亡安全**：Contract 模式配置经 Intent Extra 传递、结果经 `setResult()` 回传，均由系统托管；回调模式带优雅降级检测。
- 相机拍照落库补齐 `IS_PENDING` 两阶段发布，避免半成品文件被并发扫描读取。
- 裁剪读取 View/Matrix 移回主线程，消除 IO 线程竞争并加防崩兜底。
- 修复 `CompressHelper` 中 `inJustDecodeBounds=true` 导致提前返回 `null` 的压缩失效问题。

### 发布交付

- 随 AAR 下发 `consumer-rules.pro`，宿主开启 R8/混淆时自动合并 keep 规则，保护公开 API 与经 Intent 序列化传递的配置对象。

### 已知限制

- 数据源为 MediaStore 公共媒体，不含私有/隐藏目录。
- UI 与主题色不对外开放自定义，仅 `ThemeMode` 三档。
- 视频时长过滤仅影响列表展示，不改变磁盘原始文件。
- 部分未写入 `IS_MOTION_PHOTO` 的 OEM 机型，LIVE 角标首次进入视口时可能有极短延迟。

[1.0.0]: #100---2026-07-28
