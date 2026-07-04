# 列表 Live 角标：MediaStore 字段同步展示

日期：2026-07-04  
范围：`:photo-choice` 库模块

## 背景

网格列表 Live 角标展示滞后于缩略图，根因是 `MotionPhotoListEnricher` 对每张图片异步做 XMP 文件嗅探；即使 API 34+ 已在分页时批量查询 `IS_MOTION_PHOTO`，enricher 仍会对 `isMotionPhoto=false` 且无缓存的条目重复读文件。

## 目标

- 列表 Live 角标与 item 同帧展示，仅依赖 `MediaFile.isMotionPhoto`（分页查询时同步写入）。
- 移除列表侧 XMP 嗅探与异步 enrich 链路。
- 预览页长按播放保留 `MotionPhotoDetector.detectSingle` 按需检测（含 XMP 兜底）。

## 非目标

- API 29–33 列表不展示 Live 角标（产品已确认方案 A）。
- 不改动预览页播放、导出压缩等下游逻辑（仍可使用 preview 检测缓存）。

## 设计

### 1. 数据层：分页时同步写入 `isMotionPhoto`

| 场景 | 查询 URI | `is_motion_photo` |
|------|----------|-------------------|
| `MediaType.IMAGE` | `Images.Media.EXTERNAL_CONTENT_URI` | API 34+ 纳入 PROJECTION，Cursor 读取时赋值 |
| `MediaType.VIDEO` | `Files`（不变） | 不适用 |
| `MediaType.ALL` | `Files`（不变） | 分页返回后同步 batch 查 `Images.Media`（现有逻辑保留） |

### 2. 删除列表 enrich 链路

- 删除 `MotionPhotoListEnricher.kt`。
- `MediaGridFragment` 移除 enricher 初始化、scroll 触发、snapshot 增量 schedule。
- `MediaGridAdapter.bindLivePhotoIndicator` 简化为 `visibility = isMotionPhoto ? VISIBLE : GONE`。

### 3. 检测器职责收缩

- `MotionPhotoDetector` 列表侧移除 `quickSniffBatch`、`hasCachedResult`。
- 保留 `detectSingle`（预览）、`queryMotionIdsFromMediaStore`（ALL 模式 batch）、`isMotionPhotoCached`（预览/导出读缓存）。

### 4. 产品约定

- **列表角标**：仅 API 34+ 且 MediaStore 写入 `IS_MOTION_PHOTO=1` 时展示。
- **预览/导出**：进入预览后 `detectSingle` 仍可识别未标记实况图；导出策略可读 preview 缓存。

## 数据流（目标）

```
MediaStore 分页查询 → MediaFile.isMotionPhoto（同步）
        ↓
MediaGridAdapter.bind → livePhotoBadge 同步显隐
```

## 测试要点

- API 34+ 设备：实况图角标与缩略图同时出现，滚动无延迟闪烁。
- API 29–33：列表无 Live 角标；预览长按仍可播放（若文件含内嵌视频）。
- `MediaType.ALL`：混合列表中图片项角标仍正确。
