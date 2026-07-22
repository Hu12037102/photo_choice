# PhotoChoice

[English Documentation](README.md) | [简体中文文档](README.zh-CN.md) | [한국어 문서](README.ko.md) | [Documentation en français](README.fr.md) | [Documentación en español](README.es.md) | [الوثائق العربية](README.ar.md) | [Документация на русском](README.ru.md)

Android 向けフォトピッカーライブラリ：グリッド複数選択、アルバム切替、フルスクリーンプレビュー、任意のカメラタイル、単一画像クロップ、任意の圧縮、および **Motion Photo / Live Photo** の検出とプレビュー再生に対応。**Builder API** で統合します。内部 Activity を直接起動しないでください。

- **パッケージ**：`com.google.photochoice`
- **Min SDK**：29（Android 10、Scoped Storage。レガシー書き込み権限なしで公共メディアを読み取り可能）
- **Target SDK**：36
- **言語**：Kotlin
- **ライセンス**：[Apache License 2.0](LICENSE)

---

## 機能

| 機能 | 説明 |
|------|------|
| メディア種別 | 画像のみ / 動画のみ / 画像+動画 |
| 選択 | 単一または複数（`selectCount` 1–9） |
| アルバム | MediaStore バケット集約とドロップダウン切替 |
| グリッド | 列数設定可能（2–6）、正方形サムネイル、Paging 3 |
| スクロール日付ヘッダー | スクロール中に表示領域の日付を表示 |
| カメラ | 任意の先頭セルカメラ入口（システムギャラリーに保存） |
| プレビュー | フルスクリーンスワイプ。インライン動画再生（タップで再生、再生中のタップは UI のみ切替） |
| Motion Photo | グリッドに LIVE バッジ。長押しでプレビュー内の埋め込みクリップを再生 |
| クロップ | 単一選択 + 画像モード。独立した `CropActivity` |
| 圧縮 | 完了時に任意の JPEG リサイズ + 品質圧縮。Live Photo は動きを保持するか静止画として出力可能 |
| テーマ | ライト / ダーク / システム追従（Activity 単位、ホストアプリ全体を上書きしない） |
| 起動 API | デュアルトラック：**`PhotoChoiceContract`**（推奨、静的状態なし）または **`forResult`** コールバック |
| プロセス死亡耐性 | Contract モードは Activity 再作成とプロセス死亡に耐える。コールバックモードは優雅な劣化検出あり |

### 単一選択と複数選択

| モード | グリッド UI | 操作 |
|--------|-------------|------|
| 複数（`selectCount > 1`） | チェックボックス + 選択順バッジ | チェックボックスで切替。サムネイルタップでプレビュー |
| 単一（`selectCount = 1`） | チェックボックス、順序バッジ、無効オーバーレイを**非表示** | サムネイルタップ → プレビューまたはクロップ（有効時） |

---

## Motion Photo / Live Photo

本ライブラリは **Motion Photo、Google Motion Photo、Samsung モーションフォト** など、短い動画を埋め込んだ JPEG/HEIC をモーションフォトとして扱います（`IMAGE` タイプのまま）。

### グリッド一覧

- サムネイル左下に **LIVE** バッジ。
- **ページングをブロックしない**：ページ `load` は MediaStore の `IS_MOTION_PHOTO`（API 34+）のみ同期読み取り。XMP クイックスニフは非同期実行。
- **永続インデックス**：スキャン結果は設定変更とプロセス死亡を跨いで保持。毎回の再スニフ不要。
- **ビューポート優先**：表示領域 + プリフェッチウィンドウ専用の高優先度スニフチャネル。高速スクロールが全履歴キューにブロックされない。
- `IS_MOTION_PHOTO` を書き込まない OEM（一部端末で一般的）では、バッジは非同期 XMP ヘッド/テールスニフに依存。初回表示時にわずかな遅延（通常数百 ms 以内）。

### フルスクリーンプレビュー

- トップバー下に LIVE バッジ。
- **長押し**で埋め込み動画を再生、**離す**と停止。ピンチ/ズームで誤って停止しない。
- 入場時にバックグラウンドで検出 + 埋め込み MP4 をプリロード（`cacheDir/photo_choice_motion/` にキャッシュ）。

### 圧縮とエクスポート

`CompressConfig` 有効時、プレビューで **Live を保持 / 静止画として出力** を切替可能：

- **Live を保持**（デフォルト）：元 URI を返却、圧縮なし。
- **静止画として出力**：JPEG 圧縮、モーションは破棄。

---

## クイックスタート

### 1. モジュールの追加

ホストの `settings.gradle.kts`：

```kotlin
include(":photo-choice")
```

アプリまたは機能モジュールの `build.gradle.kts`：

```kotlin
dependencies {
    implementation(project(":photo-choice"))
}
```

> 現在は**ソースモジュール**として統合。公開後は Maven 座標に置き換えてください。

### 2. 権限

ライブラリは Manifest でメディア読み取り権限を宣言。**ホストアプリも同じ権限を宣言**し、実行時にリクエストする必要があります。

| Android バージョン | 権限 |
|-------------------|------|
| API 34+ | `READ_MEDIA_IMAGES`、`READ_MEDIA_VIDEO`（`mediaType` に応じて）、`READ_MEDIA_VISUAL_USER_SELECTED` を宣言。部分許可も利用可能として扱う |
| API 33 | `READ_MEDIA_IMAGES`、`READ_MEDIA_VIDEO`（`mediaType` に応じて） |
| API 29–32 | `READ_EXTERNAL_STORAGE` |

`PermissionHelper` で権限リストと許可チェック：

```kotlin
import com.google.photochoice.util.PermissionHelper

if (PermissionHelper.hasMediaPermission(context)) {
    openPhotoChoice()
} else {
    requestPermissionLauncher.launch(PermissionHelper.requiredMediaPermissions())
}
```

完全な例は **`sample`** / `MainActivity` を参照。

### 3. ピッカーの起動（推奨：Contract）

**プロセス死亡に耐える** `ActivityResultContract` を使用：

```kotlin
import com.google.photochoice.PhotoChoiceContract
import com.google.photochoice.PhotoChoice
import com.google.photochoice.config.MediaType

val launcher = registerForActivityResult(PhotoChoiceContract()) { result ->
    if (result == null) {
        // ユーザーがキャンセル
        return@registerForActivityResult
    }
    result.uris.forEach { uri ->
        // content:// または file:// URI
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

**Contract モード**は設定を Intent Extra で渡し、結果を `setResult()` で返却。いずれもシステム管理で、Activity 再作成とプロセス死亡に耐えます。静的変数は使用しません。**本番環境ではこちらを推奨。**

### 4. 代替：コールバック API（レガシー）

**`FragmentActivity`**（または `AppCompatActivity`）から：

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
            // ユーザーがキャンセル
            return@forResult
        }
        result.uris.forEach { uri ->
            // content:// または file:// URI
        }
    }
```

**重要：** コールバック API は内部で静的フィールドを使用し、ホスト Activity の再作成やプロセス死亡には**耐えません**。ピッカー実行中にホストが終了すると、コールバックは失われ、ピッカーは結果なしで正常終了します。信頼性が必要な場合は上記の Contract を使用してください。

---

## 結果

```kotlin
data class PhotoChoiceResult(
    val uris: List<Uri>,    // 選択順の URI リスト
    val paths: List<String> // ベストエフォートのローカルパス。解決不可時は URI 文字列
)
```

| メディア種別 | 圧縮なし | 圧縮あり |
|-------------|---------|---------|
| 静止画 | `content://` MediaStore URI | `cacheDir/photo_choice/compress_*.jpg` 下の `file://` 圧縮 JPEG |
| 動画 | `content://` MediaStore URI | 変更なし（動画は圧縮しない） |
| GIF | `content://` MediaStore URI | 変更なし（圧縮でアニメーションが失われるため） |
| Live Photo（Live 保持） | `content://` MediaStore URI | 変更なし（モーション保持） |
| Live Photo（静止画出力） | N/A | `cacheDir/photo_choice/compress_*.jpg` 下の `file://` 圧縮 JPEG |

古いキャッシュファイルのクリーンアップ：

```kotlin
PhotoChoice.cleanup(context)
```

24 時間以上経過したサンドボックスファイルを削除（必要に応じて結果処理後に呼び出し）。

---

## Builder API

| メソッド | 型 | デフォルト | 説明 |
|---------|-----|-----------|------|
| `selectCount` | `Int` | `9` | `1` = 単一、`>1` = 複数。`1..9` に自動クランプ |
| `mediaType` | `MediaType` | `IMAGE` | `IMAGE` / `VIDEO` / `ALL` |
| `spanCount` | `Int` | `3` | グリッド列数。**2–6** に自動クランプ |
| `showCamera` | `Boolean` | `true` | 先頭セルにカメラタイルを表示 |
| `minImageSize` | `Long` | `0` | 画像ファイルサイズ下限（バイト）。小さなアイコンを除外。画像のみ |
| `maxImageSize` | `Long` | `Long.MAX_VALUE` | 画像ファイルサイズ上限（バイト）。巨大画像を除外。画像のみ |
| `minVideoDuration` | `Long` | `0` | 動画最短長（ms）。maxVideoDuration より大きい場合は自動交換 |
| `maxVideoDuration` | `Long` | `60000` | 動画最長長（ms）。minVideoDuration より小さい場合は自動交換 |
| `themeMode` | `ThemeMode` | `FOLLOW_SYSTEM` | `LIGHT` / `DARK` / `FOLLOW_SYSTEM`（Activity 単位、グローバル上書きなし） |
| `cropConfig` | `CropConfig` | 下記参照 | クロップ設定 |
| `compressConfig` | `CompressConfig` | 下記参照 | 完了時の圧縮設定 |

Contract 用に個別にビルド：

```kotlin
val config = PhotoChoice.with(context)
    .selectCount(1)
    .buildConfig()  // PhotoChoiceConfig を直接返却
```

### クロップ `CropConfig`

**`selectCount = 1`** かつ **`mediaType` に画像を含む場合のみ** — 独立した `CropActivity` を起動。
動画のみまたは複数選択モードでは自動的に無効化（サイレント劣化）。

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

単一選択 + クロップ有効時、画像選択後すぐにクロップへ進み、完了後に結果を返してピッカーを閉じます。

### 圧縮 `CompressConfig`

**完了**時に**画像**をスケール + JPEG 圧縮してからコールバック。動画、GIF、Live Photo（Live 保持モード）は圧縮しません。Motion Photo はデフォルトで Live を保持。プレビューで静止画に切替えてから圧縮可能。

**デフォルト戦略（WeChat モーメント等の一般的な設定に準拠）：**

| パラメータ | デフォルト | 説明 |
|-----------|-----------|------|
| `maxWidth` / `maxHeight` | `1280` | 長辺の上限 |
| `quality` | `80` | JPEG 初期品質 |
| `maxFileSizeBytes` | `1572864`（約 1.5MB） | 超過時は品質を段階的に下げる。`0` = サイズ制限なし |
| `minQuality` | `50` | サイズ反復の下限品質 |
| `qualityStep` | `10` | 各ステップの品質減少量 |

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

> **注意：** 出力は常に JPEG です。透明 PNG/WebP は圧縮後に黒背景になります（WeChat 等と同様の挙動）。

---

## レシピ

### 複数画像（最大 9 枚）

```kotlin
PhotoChoice.with(activity)
    .selectCount(9)
    .mediaType(MediaType.IMAGE)
    .spanCount(4)
    .showCamera(true)
    .forResult(activity) { result -> /* ... */ }
```

### アバター（単一 + 正方形クロップ）

```kotlin
PhotoChoice.with(activity)
    .selectCount(1)
    .mediaType(MediaType.IMAGE)
    .cropConfig(CropConfig(enabled = true, aspectRatio = CropAspectRatio.SQUARE))
    .compressConfig(CompressConfig(enabled = true))
    .forResult(activity) { result -> /* ... */ }
```

### 動画のみ（最大 60 秒）

```kotlin
PhotoChoice.with(activity)
    .selectCount(1)
    .mediaType(MediaType.VIDEO)
    .showCamera(false)
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }
```

### 画像 + 動画

```kotlin
PhotoChoice.with(activity)
    .selectCount(9)
    .mediaType(MediaType.ALL)
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }
```

---

## サンプルアプリ

**`sample`** モジュールですべてのオプションをデモ：

```bash
./gradlew :sample:installDebug
```

**PhotoChoice Sample** を実行し、設定を調整してピッカーを開き、結果リストから選択メディアをプレビュー。

---

## アーキテクチャとパフォーマンス

### ページング

**Paging 3 + MediaStore keyset**（`DATE_ADDED` + `_ID`）— 全 Cursor スキャンなし：

| パラメータ | 例（`spanCount = 3`） |
|-----------|----------------------|
| 初回ロード | 約 15 行 × 列数 ≈ 45 件 |
| ページサイズ | 約 25 行 × 列数 ≈ 75 件 |
| プリフェッチ距離 | 約 35 行 × 列数 ≈ 105 件（約 3 画面） |
| メモリ上限 | 約 900–1200 メタデータ件（最遠ページを破棄） |

ページ `load` は **XMP 解析を実行しない** — コールドスタートと高速スクロールをスムーズに保つ。

### Motion Photo パイプライン

```
MediaStore ページ load
    ├─ 同期：API 34+ バッチ IS_MOTION_PHOTO → MediaFile.isMotionPhoto
    └─ 非同期（非ブロッキング）：
           ├─ アルバムオープン：warmAlbumFromMediaStore
           ├─ ビューポートチャネル：表示 + プリフェッチ、高優先度 XMP スニフ
           └─ バックグラウンドチャネル：低優先度プリフェッチウィンドウ
```

`data/motion/` 配下のモジュール：`MotionPhotoDetector`、`MotionPhotoListEnricher`、`MotionPhotoXmpSniffer`、`MotionPhotoVideoResolver`。

### 主要依存関係

- **Glide** — サムネイルとプレビュー画像
- **Paging 3** — グリッドページング
- **Media3 ExoPlayer** — プレビュー動画 / Motion Photo 再生
- **ViewPager2** — プレビューページング

---

## 設定の安全性

PhotoChoice はすべてのユーザー向け設定値に**防御的な正規化**を適用し、無効な入力でクラッシュしません：

| フィールド | 正規化 |
|-----------|--------|
| `selectCount` | `1..9` にクランプ。範囲外は `1` |
| `spanCount` | `2..6` にクランプ |
| `minVideoDurationMs` / `maxVideoDurationMs` | min > max の場合は自動交換。min は `>= 0` |
| `minImageSize` / `maxImageSize` | min > max の場合は自動交換。min は `>= 0` |
| `cropConfig.enabled` | VIDEO モードまたは複数選択時は自動無効（`effectiveCropEnabled`） |

---

## プロジェクト構成

```
photo_choice/
├── photo-choice/              # ライブラリ（公開 API：PhotoChoice）
│   └── src/main/java/com/google/photochoice/
│       ├── PhotoChoice.kt     # Builder エントリ、forResult()
│       ├── PhotoChoiceContract.kt     # ActivityResultContract（推奨）
│       ├── config/
│       ├── data/
│       │   └── motion/        # Motion Photo 検出、XMP、動画抽出
│       ├── viewmodel/
│       └── ui/
│           ├── grid/
│           ├── album/
│           ├── crop/          # CropActivity
│           └── preview/       # PreviewActivity、長押し Live 再生
├── sample/
├── PRD.md                     # 内部プロダクト仕様
├── README.md                  # English documentation
├── README.zh-CN.md            # 简体中文文档
├── README.ja.md               # 本文档（日本語）
├── README.ko.md               # 한국어 문서
├── README.fr.md               # Documentation en français
├── README.es.md               # Documentación en español
├── README.ar.md               # الوثائق العربية
└── README.ru.md               # Документация на русском
```

---

## ビルドと検証

```bash
./gradlew :photo-choice:assembleDebug
./gradlew :sample:installDebug
./gradlew test
./gradlew lint
```

---

## 統合チェックリスト

- [ ] `implementation(project(":photo-choice"))`（または Maven 同等物）
- [ ] ホスト Manifest にメディア読み取り権限
- [ ] 起動前の実行時権限（`PermissionHelper`）
- [ ] 起動 API の選択：**`PhotoChoiceContract`**（推奨、プロセス死亡耐性）または `forResult` コールバック
- [ ] `null`（キャンセル）と `PhotoChoiceResult`（成功）の処理
- [ ] 圧縮/クロップ使用時は必要に応じて `PhotoChoice.cleanup(context)` を呼び出し
- [ ] Live Photo + 圧縮時はプレビューの **Live を保持 / 静止画出力** の意味を理解

---

## 制限事項

- データソースは**公共 MediaStore メディア**のみ。プライベート/非表示フォルダは含まない。
- UI とアクセントカラーはカスタマイズ不可。`ThemeMode` のライト/ダーク/システムのみ。
- `PhotoChoiceActivity`、`PreviewActivity`、`CropActivity` を**直接起動しない**こと。
- 動画長フィルタは一覧表示のみに影響し、ディスク上のファイルは変更しない。
- **LIVE バッジ**：
  - API 34+ で MediaStore に `IS_MOTION_PHOTO` が設定されている場合、ほぼ即時表示。
  - DB フラグのない OEM では、初回ビューポート入場時に非同期 XMP スニフでわずかな遅延。
  - プレビューの長押しは、フラグなしの Motion Photo も完全検出（XMP 含む）で認識可能。

---

## 問題報告

**Android バージョン、端末モデル、設定スニペット、期待動作と実際の動作**を含めてください。Motion Photo の不具合の場合、システムギャラリーが Live/Motion Photo として認識しているかも記載してください。
