# PhotoChoice

[简体中文文档](README.zh-CN.md) | [日本語ドキュメント](README.ja.md) | [한국어 문서](README.ko.md) | [Documentation en français](README.fr.md) | [Documentación en español](README.es.md) | [الوثائق العربية](README.ar.md) | [Документация на русском](README.ru.md)

Android photo picker library: multi-select grid, album switching, full-screen preview, optional camera tile, single-image crop, optional compression, and **Motion Photo / Live Photo** detection with in-preview playback. Integrate via a **Builder API**—do not launch internal Activities directly.

- **Package**: `com.google.photochoice`
- **Version**: `1.1.0` (see [CHANGELOG.md](CHANGELOG.md))
- **Min SDK**: 29 (Android 10, Scoped Storage; read public media without legacy write permission)
- **Target SDK**: 36
- **Language**: Kotlin
- **License**: [Apache License 2.0](LICENSE)

---

## Features

| Feature | Description |
|---------|-------------|
| Media types | Images only / videos only / images + videos |
| Selection | Single or multi (`selectCount` 1–9) |
| Albums | MediaStore bucket aggregation with dropdown switcher |
| Grid | Configurable columns (2–6), square thumbnails, Paging 3 |
| Scroll date header | Shows date for the visible region while scrolling |
| Camera | Optional first-cell camera entry; photos are saved to `DCIM/Camera` |
| Preview | Full-screen swipe; inline video playback (tap to play, tap during playback toggles chrome only) |
| Motion Photo | LIVE badge on grid; long-press to play embedded clip in preview |
| Crop | Single-select + image mode; standalone `CropActivity` |
| Compression | Optional JPEG resize + quality on finish; live photos can keep motion or export static |
| Theme | Light / dark / follow system (per-activity, never overrides host app globally) |
| Launch API | Dual-track: **`PhotoChoiceContract`** (recommended, no static state) or **`forResult`** callback |
| Process death safety | Contract mode survives Activity recreation and process death; callback mode has graceful-degrade detection |

### Single vs multi select

| Mode | Grid UI | Interaction |
|------|---------|-------------|
| Multi (`selectCount > 1`) | Checkbox + selection order badge | Tap checkbox to toggle; tap thumbnail for preview |
| Single (`selectCount = 1`) | **Hides** checkbox, order badge, disabled overlay | Tap thumbnail → preview or crop (if enabled) |

---

## Camera capture

With `showCamera(true)` (the default), the first grid cell is a camera entry.

### Storage location and naming

| Item | Value |
|------|-------|
| Directory | `DCIM/Camera` (the public camera directory, i.e. the system "Camera" album) |
| File name | `IMG` + last 8 digits of the timestamp + 4 random digits + `.jpg`, e.g. `IMG064001234821.jpg` |
| Format | JPEG |

Photos are inserted using the MediaStore `IS_PENDING` two-phase protocol: the row only becomes visible to the system gallery after the bytes are fully written, so other apps never scan a partial file.

### Behavior after capture

| Mode | Behavior |
|------|----------|
| Multi | The photo is auto-selected; if `selectCount` is already reached, a "limit reached" message is shown and the photo still stays in the gallery |
| Single + crop enabled | Goes straight to the crop screen; cancelling the crop refreshes the list so the photo is still visible in the grid |
| Single + crop disabled | Only refreshes the list and album data; no auto-selection (single-select has no "selected" intermediate state) |

**No album switching**: the album the user is currently browsing stays unchanged; only the list and album aggregates are refreshed. If that album isn't "Camera", the new photo becomes visible after switching to it.

### What the host app must do

**Nothing.** The library declares its own `FileProvider` (authority `${applicationId}.photochoice.fileprovider`, built from the host's `applicationId` so it never clashes with other integrators), and no camera permission is required — capture goes through `ACTION_IMAGE_CAPTURE`, and the camera app holds the permission itself.

> If no camera app is installed, tapping the camera tile shows a message instead of crashing.
> If your app declares `<uses-permission android:name="android.permission.CAMERA" />` in its own Manifest, Android requires that permission to be granted before the intent can be used — that is a platform rule, not a library requirement.

### Invalid-combination fallback

When `mediaType` is `VIDEO`, the camera tile is hidden automatically (`effectiveShowCamera`): a captured still image could never appear in a video-only list, so the entry point is not shown.

---

## Motion Photo / Live Photo

The library treats **Motion Photo, Google Motion Photo, Samsung motion photos**, and similar JPEG/HEIC files with embedded short video as motion photos (still `IMAGE` type).

### Grid list

- **LIVE** badge at the bottom-left of thumbnails.
- **Does not block paging**: page `load` only sync-reads MediaStore `IS_MOTION_PHOTO` (API 34+); XMP quick sniff runs asynchronously.
- **Persistent index**: scanned results survive configuration changes and process death; no repeat sniffing on every open.
- **Viewport priority**: dedicated high-priority sniff channel for visible + prefetch window only—fast scroll is not blocked by a full-history queue.
- On OEMs that omit `IS_MOTION_PHOTO` (common on some devices), badges rely on async XMP head/tail sniff; first appearance on screen may lag briefly (typically under a few hundred ms).

### Full-screen preview

- LIVE badge below the top bar.
- **Long-press** to play embedded video, **release** to stop; pinch/zoom will not accidentally stop playback.
- Background detect + preload embedded MP4 on enter (cached under `cacheDir/photo_choice_motion/`).

### Compression & export

When `CompressConfig` is enabled, preview offers **Keep live / Export static**:

- **Keep live** (default): returns original URI, no compression.
- **Export static**: JPEG compression, motion discarded.

---

## Quick start

### 1. Add the dependency

**Option A — JitPack dependency (recommended).**

[![](https://jitpack.io/v/Hu12037102/photo_choice.svg)](https://jitpack.io/#Hu12037102/photo_choice)

Step 1 — add the JitPack repository to the host **`settings.gradle.kts`** (this project uses `FAIL_ON_PROJECT_REPOS`, so the repo must go in `dependencyResolutionManagement`, not the module):

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Step 2 — add the dependency in your app or feature module `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.Hu12037102:photo_choice:1.1.0")
}
```

> JitPack builds the AAR on demand from the tagged source; the first request for a new tag may take a minute.

**Option B — Source module.**
In the host `settings.gradle.kts`:

```kotlin
include(":photo-choice")
```

In your app or feature module `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":photo-choice"))
}
```

### 2. Permissions

The library declares media read permissions in its Manifest; **the host app must declare the same permissions** and request them at runtime.

| Android version | Permissions |
|-----------------|-------------|
| API 34+ | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` (as needed for `mediaType`), `READ_MEDIA_VISUAL_USER_SELECTED` declared; partial grant treated as usable |
| API 33 | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` (as needed for `mediaType`) |
| API 29–32 | `READ_EXTERNAL_STORAGE` |

Use `PermissionHelper` for the permission list and grant check:

```kotlin
import com.google.photochoice.util.PermissionHelper

if (PermissionHelper.hasMediaPermission(context)) {
    openPhotoChoice()
} else {
    requestPermissionLauncher.launch(PermissionHelper.requiredMediaPermissions())
}
```

See **`sample`** / `MainActivity` for a full example.

### 3. Launch the picker (recommended: Contract)

Use `ActivityResultContract` for **process-death-safe** integration:

```kotlin
import com.google.photochoice.PhotoChoiceContract
import com.google.photochoice.PhotoChoice
import com.google.photochoice.config.MediaType

val launcher = registerForActivityResult(PhotoChoiceContract()) { result ->
    if (result == null) {
        // User cancelled
        return@registerForActivityResult
    }
    result.uris.forEach { uri ->
        // content:// or file:// URI
    }
}

launcher.launch(
    PhotoChoice.with(this)
        .selectCount(9)
        .mediaType(MediaType.IMAGE)
        .spanCount(4)
        .showCamera(true)
        .buildConfig()
)
```

**The Contract mode** passes config via Intent extras, result via `setResult()`—both system-managed, surviving
Activity recreation and process death. No static variables involved. **Preferred for all production use.**

### 4. Alternative: callback API (legacy)

From a **`FragmentActivity`** (or `AppCompatActivity`):

```kotlin
import com.google.photochoice.PhotoChoice
import com.google.photochoice.config.MediaType

PhotoChoice.with(this)
    .selectCount(9)
    .mediaType(MediaType.IMAGE)
    .spanCount(4)
    .showCamera(true)
    .forResult(this) { result ->
        if (result == null) {
            // User cancelled
            return@forResult
        }
        result.uris.forEach { uri ->
            // content:// or file:// URI
        }
    }
```

**Important:** The callback API uses static fields internally and does **not** survive host Activity recreation
or process death. If the picker Activity is running while the host is killed, the callback is lost
and the picker exits cleanly without result. For reliability, use the Contract approach above.

---

## Result

```kotlin
data class PhotoChoiceResult(
    val uris: List<Uri>,    // Selected URIs in selection order
    val paths: List<String> // Best-effort local paths; URI string if unresolved
)
```

| Media type | Without compression | With compression |
|------------|---------------------|------------------|
| Static image | `content://` MediaStore URI | `file://` compressed JPEG under `cacheDir/photo_choice/compress_*.jpg` |
| Video | `content://` MediaStore URI | Untouched (videos are never compressed) |
| GIF | `content://` MediaStore URI | Untouched (compression would lose animation) |
| Live Photo (keep live) | `content://` MediaStore URI | Untouched (motion preserved) |
| Live Photo (export static) | N/A | `file://` compressed JPEG under `cacheDir/photo_choice/compress_*.jpg` |

Clean up stale cache files:

```kotlin
PhotoChoice.cleanup(context)
```

Removes sandbox files older than 24 hours (call after processing result if needed).

---

## Builder API

| Method | Type | Default | Description |
|--------|------|---------|-------------|
| `selectCount` | `Int` | `9` | `1` = single, `>1` = multi; auto-clamped to `1..9` |
| `mediaType` | `MediaType` | `IMAGE` | `IMAGE` / `VIDEO` / `ALL` |
| `spanCount` | `Int` | `3` | Grid columns; auto-clamped to **2–6** |
| `showCamera` | `Boolean` | `true` | Show camera tile as first cell; photos go to `DCIM/Camera` (see [Camera capture](#camera-capture)) |
| `minImageSize` | `Long` | `0` | Min image file size (bytes); filters out tiny icons. Images only |
| `maxImageSize` | `Long` | `Long.MAX_VALUE` | Max image file size (bytes); filters out oversized images. Images only |
| `minVideoDuration` | `Long` | `0` | Min video length (ms); auto-swapped if > maxVideoDuration |
| `maxVideoDuration` | `Long` | `60000` | Max video length (ms); auto-swapped if < minVideoDuration |
| `themeMode` | `ThemeMode` | `FOLLOW_SYSTEM` | `LIGHT` / `DARK` / `FOLLOW_SYSTEM` (per-Activity, never global) |
| `cropConfig` | `CropConfig` | see below | Crop settings |
| `compressConfig` | `CompressConfig` | see below | Compression on finish |

Build separately for Contract usage:

```kotlin
val config = PhotoChoice.with(context)
    .selectCount(1)
    .buildConfig()  // returns PhotoChoiceConfig directly
```

### Crop `CropConfig`

Only when **`selectCount = 1`** and **`mediaType` includes images**—opens standalone `CropActivity`.
The crop is automatically disabled (silently degraded) for video-only or multi-select modes.

```kotlin
import com.google.photochoice.config.CropConfig
import com.google.photochoice.config.CropAspectRatio

.cropConfig(
    CropConfig(
        enabled = true,
        aspectRatio = CropAspectRatio.SQUARE, // ORIGINAL, SQUARE, RATIO_3_4, RATIO_4_3, RATIO_9_16, RATIO_16_9
    )
)
```

With single-select + crop enabled, picking an image goes straight to crop, then returns and closes the picker.

### Compression `CompressConfig`

On **Done**, scales and JPEG-compresses **images** before callback; videos, GIFs, and live photos (keep-live mode) are not compressed. Motion photos keep live by default; switch to static in preview before compressing.

```kotlin
import com.google.photochoice.config.CompressConfig

.compressConfig(
    CompressConfig(
        enabled = true,
        maxWidth = 1280,
        maxHeight = 1280,
        quality = 80
    )
)
```

> **Note:** A `CompressHelper` bug where `inJustDecodeBounds=true` caused early return (`null`) during
> bounds-only decode has been fixed. Compression now works correctly.

---

## Recipes

### Multi image (up to 9)

```kotlin
PhotoChoice.with(activity)
    .selectCount(9)
    .mediaType(MediaType.IMAGE)
    .spanCount(4)
    .showCamera(true)
    .forResult(activity) { result -> /* ... */ }
```

### Avatar (single + square crop)

```kotlin
PhotoChoice.with(activity)
    .selectCount(1)
    .mediaType(MediaType.IMAGE)
    .cropConfig(CropConfig(enabled = true, aspectRatio = CropAspectRatio.SQUARE))
    .compressConfig(CompressConfig(enabled = true))
    .forResult(activity) { result -> /* ... */ }
```

### Video only (max 60s)

```kotlin
PhotoChoice.with(activity)
    .selectCount(1)
    .mediaType(MediaType.VIDEO)
    .showCamera(false)
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }
```

### Images + videos

```kotlin
PhotoChoice.with(activity)
    .selectCount(9)
    .mediaType(MediaType.ALL)
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }
```

---

## Sample app

The **`sample`** module demonstrates all options:

```bash
./gradlew :sample:installDebug
```

Run **PhotoChoice Sample**, tweak settings, open the picker, and preview selected media from the result list.

---

## Architecture & performance

### Paging

**Paging 3 + MediaStore keyset** (`DATE_ADDED` + `_ID`)—no full Cursor scan:

| Parameter | Example (`spanCount = 3`) |
|-----------|---------------------------|
| Initial load | ~15 rows × columns ≈ 45 items |
| Page size | ~25 rows × columns ≈ 75 items |
| Prefetch distance | ~35 rows × columns ≈ 105 items (~3 screens) |
| Memory cap | ~900–1200 metadata items (drops farthest pages) |

Page `load` **does not run XMP parsing**—cold start and fast scroll stay smooth.

### Motion photo pipeline

```
MediaStore page load
    ├─ Sync: API 34+ batch IS_MOTION_PHOTO → MediaFile.isMotionPhoto
    └─ Async (non-blocking):
           ├─ Album open: warmAlbumFromMediaStore
           ├─ Viewport channel: visible + prefetch, high-priority XMP sniff
           └─ Background channel: low-priority prefetch window
```

Modules under `data/motion/`: `MotionPhotoDetector`, `MotionPhotoListEnricher`, `MotionPhotoXmpSniffer`, `MotionPhotoVideoResolver`.

### Key dependencies

- **Glide** — thumbnails and preview images
- **Paging 3** — grid paging
- **Media3 ExoPlayer** — preview video / motion photo playback
- **ViewPager2** — preview paging

---

## Configuration safety

PhotoChoice applies **defensive sanitization** to all user-facing configuration values,
so invalid input never crashes the library:

| Field | Sanitization |
|-------|--------------|
| `selectCount` | clamped to `1..9`; falling outside returns `1` |
| `spanCount` | clamped to `2..6` |
| `minVideoDurationMs` / `maxVideoDurationMs` | auto-swapped if min > max; min clamped to `>= 0` |
| `minImageSize` / `maxImageSize` | auto-swapped if min > max; min clamped to `>= 0` |
| `cropConfig.enabled` | auto-disabled for VIDEO mode or multi-select (`effectiveCropEnabled`) |

---

## Project layout

```
photo_choice/
├── photo-choice/              # Library (public API: PhotoChoice)
│   └── src/main/java/com/google/photochoice/
│       ├── PhotoChoice.kt     # Builder entry, forResult()
│       ├── PhotoChoiceContract.kt     # ActivityResultContract (recommended)
│       ├── config/
│       ├── data/
│       │   └── motion/        # Motion photo detect, XMP, video extract
│       ├── viewmodel/
│       └── ui/
│           ├── grid/
│           ├── album/
│           ├── crop/          # CropActivity
│           └── preview/       # PreviewActivity, long-press live playback
├── sample/
├── CHANGELOG.md               # Release notes
├── README.md                  # This file (English)
├── README.zh-CN.md            # 简体中文文档
├── README.ja.md               # 日本語ドキュメント
├── README.ko.md               # 한국어 문서
├── README.fr.md               # Documentation en français
├── README.es.md               # Documentación en español
├── README.ar.md               # الوثائق العربية
└── README.ru.md               # Документация на русском
```

---

## Build & verify

```bash
./gradlew :photo-choice:assembleDebug
./gradlew :sample:installDebug
./gradlew test
./gradlew lint
```

---

## Integration checklist

- [ ] `implementation(project(":photo-choice"))` (or Maven equivalent)
- [ ] Media read permissions in host Manifest
- [ ] Runtime permission before launch (`PermissionHelper`)
- [ ] Choose launch API: **`PhotoChoiceContract`** (recommended, process-death-safe) or `forResult` callback
- [ ] Handle `null` (cancel) vs `PhotoChoiceResult` (success)
- [ ] Call `PhotoChoice.cleanup(context)` when using compress/crop if needed
- [ ] For live photos + compression, understand preview **Keep live / Export static**

---

## Limitations

- Data source is **public MediaStore media** only—not private/hidden folders.
- UI and accent colors are not customizable; only `ThemeMode` light/dark/system.
- Do **not** launch `PhotoChoiceActivity`, `PreviewActivity`, or `CropActivity` directly.
- Video duration filters affect listing only, not files on disk.
- **LIVE badges**:
  - Near-instant when API 34+ and `IS_MOTION_PHOTO` is set in MediaStore.
  - Brief delay on OEMs without DB flags (async XMP sniff on first viewport entry).
  - Preview long-press still detects unflagged motion photos via full detect (incl. XMP).

---

## Issues

Please include **Android version, device model, config snippet, expected vs actual behavior**. For motion photo bugs, note whether the system gallery recognizes the item as live/Motion Photo.
