# PhotoChoice

[English Documentation](README.md) | [简体中文文档](README.zh-CN.md) | [日本語ドキュメント](README.ja.md) | [Documentation en français](README.fr.md) | [Documentación en español](README.es.md) | [الوثائق العربية](README.ar.md) | [Документация на русском](README.ru.md)

Android 사진 선택 라이브러리: 그리드 다중 선택, 앨범 전환, 전체 화면 미리보기, 선택적 카메라 타일, 단일 이미지 자르기, 선택적 압축, **Motion Photo / Live Photo** 감지 및 미리보기 재생을 지원합니다. **Builder API**로 통합하며, 내부 Activity를 직접 실행하지 마세요.

- **패키지**: `com.google.photochoice`
- **버전**: `1.1.0` ([CHANGELOG.md](CHANGELOG.md) 참조)
- **Min SDK**: 29 (Android 10, Scoped Storage; 레거시 쓰기 권한 없이 공용 미디어 읽기 가능)
- **Target SDK**: 36
- **언어**: Kotlin
- **라이선스**: [Apache License 2.0](LICENSE)

---

## 데모

샘플 앱 기준 주요 플로우 영상 데모(약 2분):

![PhotoChoice demo](docs/demo.mp4)

영상에 나오는 기능(현재 API 기준):

| 플로우 | 내용 |
|--------|------|
| 그리드 / 앨범 | 다중 선택, 선택 순서, 앨범 전환, 스크롤 날짜 |
| 카메라 | 첫 셀 촬영 → `DCIM/Camera` |
| 미리보기 | 전체 화면 스와이프, 동영상 탭 재생 |
| Motion / Live Photo | LIVE 배지, 길게 누르기(또는 자동)로 내장 클립 재생 |
| 자르기 | 단일 선택 + 이미지 → `CropActivity` |
| 압축 | 완료 시 JPEG 압축, Live **유지 / 정지 내보내기** |
| 테마 / 실행 | 라이트/다크/시스템, Contract / 콜백 |

> 파일: [`docs/demo.mp4`](docs/demo.mp4) · [`docs/demo-cover.jpg`](docs/demo-cover.jpg)

---

## 기능

| 기능 | 설명 |
|------|------|
| 미디어 유형 | 이미지만 / 동영상만 / 이미지+동영상 |
| 선택 | 단일 또는 다중 (`selectCount` 1–9) |
| 앨범 | MediaStore 버킷 집계 및 드롭다운 전환 |
| 그리드 | 열 수 설정 가능 (2–6), 정사각형 썸네일, Paging 3 |
| 스크롤 날짜 헤더 | 스크롤 중 표시 영역의 날짜 표시 |
| 카메라 | 선택적 첫 번째 셀 카메라 진입점, 사진은 `DCIM/Camera`에 저장 |
| 미리보기 | 전체 화면 스와이프; 인라인 동영상 재생 (탭하여 재생, 재생 중 탭은 UI만 전환) |
| Motion Photo | 그리드에 LIVE 배지; 길게 눌러 미리보기에서 내장 클립 재생 |
| 자르기 | 단일 선택 + 이미지 모드; 독립 `CropActivity` |
| 압축 | 완료 시 선택적 JPEG 크기 조정 + 품질 압축; Live Photo는 동작 유지 또는 정지 이미지로보내기 가능 |
| 테마 | 라이트 / 다크 / 시스템 따름 (Activity 단위, 호스트 앱 전체를 덮어쓰지 않음) |
| 실행 API | 이중 트랙: **`PhotoChoiceContract`** (권장, 정적 상태 없음) 또는 **`forResult`** 콜백 |
| 프로세스 종료 안전성 | Contract 모드는 Activity 재생성 및 프로세스 종료에 견딤; 콜백 모드는 우아한 저하 감지 |

### 단일 선택 vs 다중 선택

| 모드 | 그리드 UI | 상호작용 |
|------|-----------|----------|
| 다중 (`selectCount > 1`) | 체크박스 + 선택 순서 배지 | 체크박스로 전환; 썸네일 탭하여 미리보기 |
| 단일 (`selectCount = 1`) | 체크박스, 순서 배지, 비활성 오버레이 **숨김** | 썸네일 탭 → 미리보기 또는 자르기 (활성화 시) |

---

## 카메라 촬영

`showCamera(true)`(기본값)일 때 그리드 첫 번째 셀이 카메라 진입점이 됩니다.

### 저장 위치와 파일명

| 항목 | 값 |
|------|-----|
| 디렉터리 | `DCIM/Camera` (공용 카메라 디렉터리, 시스템 "카메라" 앨범) |
| 파일명 | `IMG` + 타임스탬프 뒤 8자리 + 랜덤 4자리 + `.jpg`, 예: `IMG064001234821.jpg` |
| 형식 | JPEG |

사진은 MediaStore `IS_PENDING` 2단계 프로토콜로 등록됩니다. 바이트 쓰기가 끝나기 전까지 시스템 갤러리에 노출되지 않으므로 다른 앱이 미완성 파일을 스캔하지 않습니다.

### 촬영 후 동작

| 모드 | 동작 |
|------|------|
| 다중 선택 | 촬영한 사진이 자동으로 선택됨. `selectCount` 상한에 도달한 경우 "최대 선택 수량 도달" 메시지를 표시하며, 사진 자체는 앨범에 그대로 저장됨 |
| 단일 선택 + 자르기 활성 | 곧바로 자르기 화면으로 이동. 자르기를 취소하면 목록을 새로고침하여 사진을 그리드에서 확인 가능 |
| 단일 선택 + 자르기 비활성 | 목록과 앨범 데이터만 새로고침, 자동 선택 없음 (단일 선택에는 "선택됨" 중간 상태가 없음) |

**앨범을 전환하지 않습니다**: 사용자가 보고 있던 앨범은 그대로 유지되며 목록과 앨범 집계만 갱신됩니다. 현재 앨범이 "카메라"가 아니라면 해당 앨범으로 전환한 뒤에 새 사진이 보입니다.

### 호스트 앱에서 할 일

**없습니다.** 라이브러리가 자체적으로 `FileProvider`를 선언하며(authority는 `${applicationId}.photochoice.fileprovider`로 호스트의 `applicationId`에서 생성되어 다른 통합 앱과 충돌하지 않음), 카메라 권한도 필요하지 않습니다 — 촬영은 `ACTION_IMAGE_CAPTURE`로 이루어지며 권한은 카메라 앱이 보유합니다.

> 카메라 앱이 하나도 없는 기기에서는 카메라 타일을 눌러도 크래시 대신 안내 메시지를 표시합니다.
> 호스트 앱이 자체 Manifest에 `<uses-permission android:name="android.permission.CAMERA" />`를 선언한 경우, Android 규칙상 해당 권한을 먼저 승인받아야 합니다. 이는 플랫폼 규정이며 본 라이브러리의 요구사항이 아닙니다.

### 잘못된 조합 폴백

`mediaType`이 `VIDEO`이면 카메라 타일이 자동으로 숨겨집니다(`effectiveShowCamera`). 촬영 결과인 정지 이미지는 영상만 조회하는 목록에 나타날 수 없으므로 진입점 자체를 노출하지 않습니다.

---

## Motion Photo / Live Photo

라이브러리는 **Motion Photo, Google Motion Photo, Samsung 모션 사진** 등 짧은 동영상이 내장된 JPEG/HEIC를 모션 사진으로 처리합니다 (여전히 `IMAGE` 유형).

### 그리드 목록

- 썸네일 왼쪽 하단에 **LIVE** 배지.
- **페이징을 차단하지 않음**: 페이지 `load`는 MediaStore `IS_MOTION_PHOTO` (API 34+)만 동기 읽기; XMP 빠른 스니핑은 비동기 실행.
- **영구 인덱스**: 스캔 결과가 구성 변경 및 프로세스 종료를 넘어 유지; 매번 재스니핑 불필요.
- **뷰포트 우선**: 표시 영역 + 프리페치 윈도우 전용 고우선순위 스니핑 채널. 빠른 스크롤이 전체 기록 큐에 차단되지 않음.
- `IS_MOTION_PHOTO`를 쓰지 않는 OEM (일부 기기에서 흔함)에서는 배지가 비동기 XMP 헤드/테일 스니핑에 의존. 첫 화면 표시 시 짧은 지연 (보통 수백 ms 이내).

### 전체 화면 미리보기

- 상단 바 아래 LIVE 배지.
- **길게 누르기**로 내장 동영상 재생, **떼면** 정지. 핀치/줌으로 실수로 정지되지 않음.
- 진입 시 백그라운드에서 감지 + 내장 MP4 프리로드 (`cacheDir/photo_choice_motion/`에 캐시).

### 압축 및보내기

`CompressConfig` 활성화 시 미리보기에서 **Live 유지 / 정지 이미지로보내기** 전환 가능:

- **Live 유지** (기본값): 원본 URI 반환, 압축 없음.
- **정지 이미지로보내기**: JPEG 압축, 모션 폐기.

---

## 빠른 시작

### 1. 의존성 추가

**방법 A — JitPack 의존성 (권장).**

[![](https://jitpack.io/v/Hu12037102/photo_choice.svg)](https://jitpack.io/#Hu12037102/photo_choice)

1단계 — 호스트 **`settings.gradle.kts`** 에 JitPack 저장소 추가 (이 프로젝트는 `FAIL_ON_PROJECT_REPOS` 를 사용하므로 저장소는 모듈의 `build.gradle.kts` 가 아닌 `dependencyResolutionManagement` 에 작성):

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

2단계 — 앱 또는 기능 모듈 `build.gradle.kts` 에 의존성 추가:

```kotlin
dependencies {
    implementation("com.github.Hu12037102:photo_choice:1.1.0")
}
```

> JitPack 은 tag 소스에서 AAR 을 온디맨드로 빌드합니다. 새 tag 의 첫 요청은 1분 정도 걸릴 수 있습니다.

**방법 B — 소스 모듈.**
호스트 `settings.gradle.kts`:

```kotlin
include(":photo-choice")
```

앱 또는 기능 모듈 `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":photo-choice"))
}
```

### 2. 권한

라이브러리는 Manifest에서 미디어 읽기 권한을 선언합니다. **호스트 앱도 동일한 권한을 선언**하고 런타임에 요청해야 합니다.

| Android 버전 | 권한 |
|-------------|------|
| API 34+ | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` (`mediaType`에 따라), `READ_MEDIA_VISUAL_USER_SELECTED` 선언; 부분 허용도 사용 가능으로 처리 |
| API 33 | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` (`mediaType`에 따라) |
| API 29–32 | `READ_EXTERNAL_STORAGE` |

`PermissionHelper`로 권한 목록 및 허용 확인:

```kotlin
import com.google.photochoice.util.PermissionHelper

if (PermissionHelper.hasMediaPermission(context)) {
    openPhotoChoice()
} else {
    requestPermissionLauncher.launch(PermissionHelper.requiredMediaPermissions())
}
```

전체 예제는 **`sample`** / `MainActivity` 참조.

### 3. 피커 실행 (권장: Contract)

**프로세스 종료에 안전한** `ActivityResultContract` 사용:

```kotlin
import com.google.photochoice.PhotoChoiceContract
import com.google.photochoice.PhotoChoice
import com.google.photochoice.config.MediaType

val launcher = registerForActivityResult(PhotoChoiceContract()) { result ->
    if (result == null) {
        // 사용자 취소
        return@registerForActivityResult
    }
    result.uris.forEach { uri ->
        // content:// 또는 file:// URI
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

**Contract 모드**는 설정을 Intent Extra로 전달하고 결과를 `setResult()`로 반환 — 모두 시스템 관리로 Activity 재생성 및 프로세스 종료에 견딥니다. 정적 변수 없음. **프로덕션 환경에서 권장.**

### 4. 대안: 콜백 API (레거시)

**`FragmentActivity`** (또는 `AppCompatActivity`)에서:

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
            // 사용자 취소
            return@forResult
        }
        result.uris.forEach { uri ->
            // content:// 또는 file:// URI
        }
    }
```

**중요:** 콜백 API는 내부적으로 정적 필드를 사용하며 호스트 Activity 재생성이나 프로세스 종료에 **견디지 못합니다**. 피커 실행 중 호스트가 종료되면 콜백이 손실되고 피커는 결과 없이 정상 종료됩니다. 안정성이 필요하면 위의 Contract를 사용하세요.

---

## 결과

```kotlin
data class PhotoChoiceResult(
    val uris: List<Uri>,    // 선택 순서의 URI 목록
    val paths: List<String> // 최선의 로컬 경로; 해결 불가 시 URI 문자열
)
```

| 미디어 유형 | 압축 없음 | 압축 있음 |
|------------|----------|----------|
| 정지 이미지 | `content://` MediaStore URI | `cacheDir/photo_choice/compress_*.jpg` 아래 `file://` 압축 JPEG |
| 동영상 | `content://` MediaStore URI | 변경 없음 (동영상은 압축하지 않음) |
| GIF | `content://` MediaStore URI | 변경 없음 (압축 시 애니메이션 손실) |
| Live Photo (Live 유지) | `content://` MediaStore URI | 변경 없음 (모션 유지) |
| Live Photo (정지보내기) | N/A | `cacheDir/photo_choice/compress_*.jpg` 아래 `file://` 압축 JPEG |

오래된 캐시 파일 정리:

```kotlin
PhotoChoice.cleanup(context)
```

24시간 이상 된 샌드박스 파일 제거 (필요 시 결과 처리 후 호출).

---

## Builder API

| 메서드 | 유형 | 기본값 | 설명 |
|--------|------|--------|------|
| `selectCount` | `Int` | `9` | `1` = 단일, `>1` = 다중. `1..9`로 자동 클램프 |
| `mediaType` | `MediaType` | `IMAGE` | `IMAGE` / `VIDEO` / `ALL` |
| `spanCount` | `Int` | `3` | 그리드 열 수. **2–6**으로 자동 클램프 |
| `showCamera` | `Boolean` | `true` | 첫 번째 셀에 카메라 타일 표시. 사진은 `DCIM/Camera`에 저장 ([카메라 촬영](#카메라-촬영) 참조) |
| `minImageSize` | `Long` | `0` | 이미지 파일 크기 하한 (바이트). 작은 아이콘 필터링. 이미지만 |
| `maxImageSize` | `Long` | `Long.MAX_VALUE` | 이미지 파일 크기 상한 (바이트). 대용량 이미지 필터링. 이미지만 |
| `minVideoDuration` | `Long` | `0` | 동영상 최소 길이 (ms). maxVideoDuration보다 크면 자동 교환 |
| `maxVideoDuration` | `Long` | `60000` | 동영상 최대 길이 (ms). minVideoDuration보다 작으면 자동 교환 |
| `themeMode` | `ThemeMode` | `FOLLOW_SYSTEM` | `LIGHT` / `DARK` / `FOLLOW_SYSTEM` (Activity 단위, 전역 덮어쓰기 없음) |
| `cropConfig` | `CropConfig` | 아래 참조 | 자르기 설정 |
| `compressConfig` | `CompressConfig` | 아래 참조 | 완료 시 압축 설정 |

Contract용 별도 빌드:

```kotlin
val config = PhotoChoice.with(context)
    .selectCount(1)
    .buildConfig()  // PhotoChoiceConfig 직접 반환
```

### 자르기 `CropConfig`

**`selectCount = 1`** 이고 **`mediaType`에 이미지 포함**일 때만 — 독립 `CropActivity` 실행.
동영상 전용 또는 다중 선택 모드에서는 자동 비활성화 (조용한 저하).

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

단일 선택 + 자르기 활성화 시 이미지 선택 후 바로 자르기로 이동, 완료 후 결과 반환 및 피커 종료.

### 압축 `CompressConfig`

**완료** 시 **이미지**를 크기 조정 + JPEG 압축 후 콜백. 동영상, GIF, Live Photo (Live 유지 모드)는 압축하지 않음. Motion Photo는 기본적으로 Live 유지. 미리보기에서 정지 이미지로 전환 후 압축 가능.

**기본 전략 (WeChat 모멘트 등 일반적인 설정에 맞춤):**

| 매개변수 | 기본값 | 설명 |
|---------|--------|------|
| `maxWidth` / `maxHeight` | `1280` | 긴 변 상한 |
| `quality` | `80` | JPEG 시작 품질 |
| `maxFileSizeBytes` | `1572864` (약 1.5MB) | 초과 시 품질 단계적 감소. `0` = 크기 제한 없음 |
| `minQuality` | `50` | 크기 반복 하한 품질 |
| `qualityStep` | `10` | 각 단계 품질 감소량 |

```kotlin
import com.google.photochoice.config.CompressConfig

.compressConfig(
    CompressConfig(
        enabled = true,
        maxWidth = 1280,
        maxHeight = 1280,
        quality = 80,
        maxFileSizeBytes = CompressConfig.DEFAULT_MAX_FILE_SIZE_BYTES,
        minQuality = 50,
        qualityStep = 10
    )
)
```

> **참고:** 출력은 항상 JPEG입니다. 투명 PNG/WebP는 압축 후 검은 배경이 됩니다 (WeChat 등과 동일한 동작).

---

## 레시피

### 다중 이미지 (최대 9장)

```kotlin
PhotoChoice.with(activity)
    .selectCount(9)
    .mediaType(MediaType.IMAGE)
    .spanCount(4)
    .showCamera(true)
    .forResult(activity) { result -> /* ... */ }
```

### 아바타 (단일 + 정사각형 자르기)

```kotlin
PhotoChoice.with(activity)
    .selectCount(1)
    .mediaType(MediaType.IMAGE)
    .cropConfig(CropConfig(enabled = true, aspectRatio = CropAspectRatio.SQUARE))
    .compressConfig(CompressConfig(enabled = true))
    .forResult(activity) { result -> /* ... */ }
```

### 동영상만 (최대 60초)

```kotlin
PhotoChoice.with(activity)
    .selectCount(1)
    .mediaType(MediaType.VIDEO)
    .showCamera(false)
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }
```

### 이미지 + 동영상

```kotlin
PhotoChoice.with(activity)
    .selectCount(9)
    .mediaType(MediaType.ALL)
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }
```

---

## 샘플 앱

**`sample`** 모듈에서 모든 옵션 데모:

```bash
./gradlew :sample:installDebug
```

**PhotoChoice Sample** 실행, 설정 조정 후 피커 열기, 결과 목록에서 선택 미디어 미리보기.

---

## 아키텍처 및 성능

### 페이징

**Paging 3 + MediaStore keyset** (`DATE_ADDED` + `_ID`) — 전체 Cursor 스캔 없음:

| 매개변수 | 예 (`spanCount = 3`) |
|---------|---------------------|
| 초기 로드 | 약 15행 × 열 수 ≈ 45개 |
| 페이지 크기 | 약 25행 × 열 수 ≈ 75개 |
| 프리페치 거리 | 약 35행 × 열 수 ≈ 105개 (약 3화면) |
| 메모리 상한 | 약 900–1200 메타데이터 항목 (가장 먼 페이지 폐기) |

페이지 `load`는 **XMP 파싱을 실행하지 않음** — 콜드 스타트와 빠른 스크롤 유지.

### Motion Photo 파이프라인

```
MediaStore 페이지 load
    ├─ 동기: API 34+ 배치 IS_MOTION_PHOTO → MediaFile.isMotionPhoto
    └─ 비동기 (비차단):
           ├─ 앨범 열기: warmAlbumFromMediaStore
           ├─ 뷰포트 채널: 표시 + 프리페치, 고우선순위 XMP 스니핑
           └─ 백그라운드 채널: 저우선순위 프리페치 윈도우
```

`data/motion/` 하위 모듈: `MotionPhotoDetector`, `MotionPhotoListEnricher`, `MotionPhotoXmpSniffer`, `MotionPhotoVideoResolver`.

### 주요 의존성

- **Glide** — 썸네일 및 미리보기 이미지
- **Paging 3** — 그리드 페이징
- **Media3 ExoPlayer** — 미리보기 동영상 / Motion Photo 재생
- **ViewPager2** — 미리보기 페이징

---

## 설정 안전성

PhotoChoice는 모든 사용자 대면 설정 값에 **방어적 정규화**를 적용하여 잘못된 입력으로 크래시하지 않습니다:

| 필드 | 정규화 |
|------|--------|
| `selectCount` | `1..9`로 클램프. 범위 밖은 `1` |
| `spanCount` | `2..6`으로 클램프 |
| `minVideoDurationMs` / `maxVideoDurationMs` | min > max 시 자동 교환. min은 `>= 0` |
| `minImageSize` / `maxImageSize` | min > max 시 자동 교환. min은 `>= 0` |
| `cropConfig.enabled` | VIDEO 모드 또는 다중 선택 시 자동 비활성화 (`effectiveCropEnabled`) |

---

## 프로젝트 구조

```
photo_choice/
├── photo-choice/              # 라이브러리 (공개 API: PhotoChoice)
│   └── src/main/java/com/google/photochoice/
│       ├── PhotoChoice.kt     # Builder 진입점, forResult()
│       ├── PhotoChoiceContract.kt     # ActivityResultContract (권장)
│       ├── config/
│       ├── data/
│       │   └── motion/        # Motion Photo 감지, XMP, 동영상 추출
│       ├── viewmodel/
│       └── ui/
│           ├── grid/
│           ├── album/
│           ├── crop/          # CropActivity
│           └── preview/       # PreviewActivity, 길게 누르기 Live 재생
├── sample/
├── docs/
│   ├── demo.mp4               # README demo video
│   └── demo-cover.jpg         # Demo cover frame
├── CHANGELOG.md               # 변경 이력
├── README.md                  # English documentation
├── README.zh-CN.md            # 简体中文文档
├── README.ja.md               # 日本語ドキュメント
├── README.ko.md               # 본 문서 (한국어)
├── README.fr.md               # Documentation en français
├── README.es.md               # Documentación en español
├── README.ar.md               # الوثائق العربية
└── README.ru.md               # Документация на русском
```

---

## 빌드 및 검증

```bash
./gradlew :photo-choice:assembleDebug
./gradlew :sample:installDebug
./gradlew test
./gradlew lint
```

---

## 통합 체크리스트

- [ ] `implementation(project(":photo-choice"))` (또는 Maven 동등물)
- [ ] 호스트 Manifest에 미디어 읽기 권한
- [ ] 실행 전 런타임 권한 (`PermissionHelper`)
- [ ] 실행 API 선택: **`PhotoChoiceContract`** (권장, 프로세스 종료 안전) 또는 `forResult` 콜백
- [ ] `null` (취소) vs `PhotoChoiceResult` (성공) 처리
- [ ] 압축/자르기 사용 시 필요에 따라 `PhotoChoice.cleanup(context)` 호출
- [ ] Live Photo + 압축 시 미리보기 **Live 유지 / 정지보내기** 의미 이해

---

## 제한 사항

- 데이터 소스는 **공용 MediaStore 미디어**만 — 비공개/숨김 폴더 미포함.
- UI 및 강조 색상은 사용자 정의 불가. `ThemeMode` 라이트/다크/시스템만 지원.
- `PhotoChoiceActivity`, `PreviewActivity`, `CropActivity`를 **직접 실행하지 마세요**.
- 동영상 길이 필터는 목록 표시에만 영향, 디스크 파일은 변경하지 않음.
- **LIVE 배지**:
  - API 34+에서 MediaStore에 `IS_MOTION_PHOTO` 설정 시 거의 즉시 표시.
  - DB 플래그 없는 OEM에서는 첫 뷰포트 진입 시 비동기 XMP 스니핑으로 짧은 지연.
  - 미리보기 길게 누르기는 플래그 없는 Motion Photo도 전체 감지 (XMP 포함)로 인식 가능.

---

## 문제 보고

**Android 버전, 기기 모델, 설정 스니펫, 예상 동작 vs 실제 동작**을 포함해 주세요. Motion Photo 버그의 경우 시스템 갤러리가 Live/Motion Photo로 인식하는지도 기재해 주세요.
