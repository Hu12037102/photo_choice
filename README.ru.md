# PhotoChoice

[English Documentation](README.md) | [简体中文文档](README.zh-CN.md) | [日本語ドキュメント](README.ja.md) | [한국어 문서](README.ko.md) | [Documentation en français](README.fr.md) | [Documentación en español](README.es.md) | [الوثائق العربية](README.ar.md)

Библиотека выбора фото для Android: сетка с множественным выбором, переключение альбомов, полноэкранный предпросмотр, опциональная плитка камеры, обрезка одного изображения, опциональное сжатие и обнаружение **Motion Photo / Live Photo** с воспроизведением в предпросмотре. Интеграция через **Builder API** — не запускайте внутренние Activity напрямую.

- **Пакет**: `com.google.photochoice`
- **Версия**: `1.1.0` (см. [CHANGELOG.md](CHANGELOG.md))
- **Min SDK**: 29 (Android 10, Scoped Storage; чтение публичных медиа без устаревшего разрешения на запись)
- **Target SDK**: 36
- **Язык**: Kotlin
- **Лицензия**: [Apache License 2.0](LICENSE)

---

## Демо

Видео (~2 мин) основных сценариев на sample-приложении:

![PhotoChoice demo](docs/demo.mp4)

Что показано (текущий API):

| Сценарий | Содержание |
|----------|------------|
| Сетка / альбомы | Мультивыбор, порядок, альбомы, дата при прокрутке |
| Камера | Плитка камеры → `DCIM/Camera` |
| Предпросмотр | Полноэкранный свайп, видео по нажатию |
| Motion / Live Photo | Значок LIVE, long-press (или авто) встроенного клипа |
| Обрезка | Одиночный выбор + изображение → `CropActivity` |
| Сжатие | JPEG при завершении; Live **сохранить / статический экспорт** |
| Тема / запуск | Светлая/тёмная/система; Contract / callback |

> Файлы: [`docs/demo.mp4`](docs/demo.mp4) · [`docs/demo-cover.jpg`](docs/demo-cover.jpg)

### Sample APK

Отсканируйте QR-код или скачайте демо-APK по ссылке:

[![QR-код Sample APK](docs/sample-apk-qr.png)](https://huxiaobai.oss-cn-shanghai.aliyuncs.com/open/sample-release.apk)

**[Скачать sample-release.apk](https://huxiaobai.oss-cn-shanghai.aliyuncs.com/open/sample-release.apk)**


---

## Возможности

| Возможность | Описание |
|-------------|----------|
| Типы медиа | Только изображения / только видео / изображения + видео |
| Выбор | Одиночный или множественный (`selectCount` 1–9) |
| Альбомы | Агрегация bucket MediaStore с выпадающим переключателем |
| Сетка | Настраиваемые столбцы (2–6), квадратные миниатюры, Paging 3 |
| Заголовок даты при прокрутке | Показывает дату видимой области при прокрутке |
| Камера | Опциональная плитка камеры в первой ячейке; снимки сохраняются в `DCIM/Camera` |
| Предпросмотр | Полноэкранная прокрутка; встроенное воспроизведение видео (нажатие для воспроизведения, нажатие во время воспроизведения переключает только интерфейс) |
| Motion Photo | Значок LIVE на сетке; долгое нажатие для воспроизведения встроенного клипа в предпросмотре |
| Обрезка | Одиночный выбор + режим изображения; автономный `CropActivity` |
| Сжатие | Опциональное изменение размера JPEG + качество при завершении; Live Photo может сохранить движение или экспортировать как статичное изображение |
| Тема | Светлая / тёмная / следовать системе (на уровне Activity, никогда не переопределяет хост-приложение глобально) |
| API запуска | Двойной путь: **`PhotoChoiceContract`** (рекомендуется, без статического состояния) или callback **`forResult`** |
| Устойчивость к process death | Режим Contract переживает пересоздание Activity и process death; режим callback имеет плавное обнаружение деградации |

### Одиночный vs множественный выбор

| Режим | UI сетки | Взаимодействие |
|-------|----------|----------------|
| Множественный (`selectCount > 1`) | Флажок + значок порядка выбора | Нажатие на флажок для переключения; нажатие на миниатюру для предпросмотра |
| Одиночный (`selectCount = 1`) | **Скрывает** флажок, значок порядка, оверлей отключения | Нажатие на миниатюру → предпросмотр или обрезка (если включено) |

---

## Съёмка на камеру

При `showCamera(true)` (значение по умолчанию) первая ячейка сетки служит точкой входа в камеру.

### Расположение и имя файла

| Параметр | Значение |
|----------|----------|
| Каталог | `DCIM/Camera` (публичный каталог камеры, то есть системный альбом «Камера») |
| Имя файла | `IMG` + последние 8 цифр метки времени + 4 случайные цифры + `.jpg`, например `IMG064001234821.jpg` |
| Формат | JPEG |

Снимки добавляются по двухфазному протоколу `IS_PENDING` в MediaStore: запись становится видимой системной галерее только после полной записи байтов, поэтому другие приложения никогда не сканируют незавершённый файл.

### Поведение после съёмки

| Режим | Поведение |
|-------|-----------|
| Множественный выбор | Снимок выбирается автоматически; если лимит `selectCount` уже достигнут, показывается сообщение «достигнут лимит», а сам снимок остаётся сохранённым в галерее |
| Одиночный выбор + обрезка включена | Сразу открывается экран обрезки; при отмене обрезки список обновляется, и снимок остаётся виден в сетке |
| Одиночный выбор + обрезка выключена | Обновляются только список и данные альбомов, без автовыбора (в одиночном режиме нет промежуточного состояния «выбрано») |

**Альбом не переключается**: альбом, который просматривает пользователь, остаётся прежним; обновляются только список и агрегаты альбомов. Если это не альбом «Камера», новый снимок станет виден после переключения на него.

### Что нужно сделать хост-приложению

**Ничего.** Библиотека сама объявляет `FileProvider` (authority — `${applicationId}.photochoice.fileprovider`, формируется из `applicationId` хоста, поэтому не конфликтует с другими интеграторами), и разрешение камеры не требуется: съёмка идёт через `ACTION_IMAGE_CAPTURE`, а разрешением владеет само приложение камеры.

> Если на устройстве нет ни одного приложения камеры, нажатие на плитку камеры показывает сообщение, а не приводит к сбою.
> Если ваше приложение объявляет `<uses-permission android:name="android.permission.CAMERA" />` в собственном Manifest, Android потребует выдать это разрешение перед использованием интента — это правило платформы, а не требование библиотеки.

### Откат при недопустимой комбинации

Когда `mediaType` равен `VIDEO`, плитка камеры автоматически скрывается (`effectiveShowCamera`): снятое фото никогда не появится в списке, содержащем только видео, поэтому точка входа не показывается.

---

## Motion Photo / Live Photo

Библиотека обрабатывает **Motion Photo, Google Motion Photo, фото в движении Samsung** и аналогичные JPEG/HEIC с встроенным коротким видео как motion photos (по-прежнему тип `IMAGE`).

### Список сетки

- Значок **LIVE** в левом нижнем углу миниатюр.
- **Не блокирует пагинацию**: `load` страницы только синхронно читает `IS_MOTION_PHOTO` из MediaStore (API 34+); быстрый XMP sniff выполняется асинхронно.
- **Постоянный индекс**: результаты сканирования сохраняются при смене конфигурации и process death; без повторного sniff при каждом открытии.
- **Приоритет viewport**: выделенный высокоприоритетный канал sniff только для видимого окна + prefetch — быстрая прокрутка не блокируется полной исторической очередью.
- На OEM без `IS_MOTION_PHOTO` (распространено на некоторых устройствах) значки зависят от асинхронного XMP head/tail sniff; первое появление на экране может немного задержаться (обычно менее нескольких сотен мс).

### Полноэкранный предпросмотр

- Значок LIVE под верхней панелью.
- **Долгое нажатие** для воспроизведения встроенного видео, **отпускание** для остановки; pinch/zoom не останавливает воспроизведение случайно.
- Фоновое обнаружение + предзагрузка встроенного MP4 при входе (кэш в `cacheDir/photo_choice_motion/`).

### Сжатие и экспорт

При включённом `CompressConfig` предпросмотр предлагает **Сохранить live / Экспортировать статичное**:

- **Сохранить live** (по умолчанию): возвращает исходный URI, без сжатия.
- **Экспортировать статичное**: JPEG-сжатие, движение отбрасывается.

---

## Быстрый старт

### 1. Добавление зависимости

**Вариант A — Зависимость JitPack (рекомендуется).**

[![](https://jitpack.io/v/Hu12037102/photo_choice.svg)](https://jitpack.io/#Hu12037102/photo_choice)

Шаг 1 — добавьте репозиторий JitPack в `settings.gradle.kts` хоста (этот проект использует `FAIL_ON_PROJECT_REPOS`, поэтому репозиторий должен быть в `dependencyResolutionManagement`, а не в `build.gradle.kts` модуля):

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Шаг 2 — добавьте зависимость в `build.gradle.kts` приложения или функционального модуля:

```kotlin
dependencies {
    implementation("com.github.Hu12037102:photo_choice:1.1.0")
}
```

> JitPack собирает AAR по запросу из исходников тега; первый запрос нового тега может занять около минуты.

**Вариант B — Исходный модуль.**
В `settings.gradle.kts` хоста:

```kotlin
include(":photo-choice")
```

В `build.gradle.kts` приложения или функционального модуля:

```kotlin
dependencies {
    implementation(project(":photo-choice"))
}
```

### 2. Разрешения

Библиотека объявляет разрешения на чтение медиа в Manifest; **хост-приложение должно объявить те же разрешения** и запрашивать их во время выполнения.

| Версия Android | Разрешения |
|----------------|------------|
| API 34+ | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` (по `mediaType`), `READ_MEDIA_VISUAL_USER_SELECTED` объявлен; частичное предоставление считается допустимым |
| API 33 | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` (по `mediaType`) |
| API 29–32 | `READ_EXTERNAL_STORAGE` |

Используйте `PermissionHelper` для списка разрешений и проверки:

```kotlin
import com.google.photochoice.util.PermissionHelper

if (PermissionHelper.hasMediaPermission(context)) {
    openPhotoChoice()
} else {
    requestPermissionLauncher.launch(PermissionHelper.requiredMediaPermissions())
}
```

Полный пример см. в **`sample`** / `MainActivity`.

### 3. Запуск пикера (рекомендуется: Contract)

Используйте `ActivityResultContract` для интеграции **устойчивой к process death**:

```kotlin
import com.google.photochoice.PhotoChoiceContract
import com.google.photochoice.PhotoChoice
import com.google.photochoice.config.MediaType

val launcher = registerForActivityResult(PhotoChoiceContract()) { result ->
    if (result == null) {
        // Пользователь отменил
        return@registerForActivityResult
    }
    result.uris.forEach { uri ->
        // URI content:// или file://
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

**Режим Contract** передаёт конфиг через Intent extras, результат через `setResult()` — оба управляются системой, переживают пересоздание Activity и process death. Без статических переменных. **Предпочтителен для production.**

### 4. Альтернатива: callback API (legacy)

Из **`FragmentActivity`** (или `AppCompatActivity`):

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
            // Пользователь отменил
            return@forResult
        }
        result.uris.forEach { uri ->
            // URI content:// или file://
        }
    }
```

**Важно:** Callback API использует статические поля внутри и **не переживает** пересоздание хост-Activity или process death. Если Activity пикера работает, пока хост завершён, callback теряется и пикер корректно закрывается без результата. Для надёжности используйте Contract выше.

---

## Результат

```kotlin
data class PhotoChoiceResult(
    val uris: List<Uri>,    // Выбранные URI в порядке выбора
    val paths: List<String> // Локальные пути best-effort; строка URI если не разрешено
)
```

| Тип медиа | Без сжатия | Со сжатием |
|-----------|------------|------------|
| Статичное изображение | URI MediaStore `content://` | Сжатый JPEG `file://` в `cacheDir/photo_choice/compress_*.jpg` |
| Видео | URI MediaStore `content://` | Без изменений (видео никогда не сжимаются) |
| GIF | URI MediaStore `content://` | Без изменений (сжатие потеряет анимацию) |
| Live Photo (сохранить live) | URI MediaStore `content://` | Без изменений (движение сохранено) |
| Live Photo (экспорт статичного) | N/A | Сжатый JPEG `file://` в `cacheDir/photo_choice/compress_*.jpg` |

Очистка устаревших файлов кэша:

```kotlin
PhotoChoice.cleanup(context)
```

Удаляет файлы sandbox старше 24 часов (вызывайте после обработки результата при необходимости).

---

## Builder API

| Метод | Тип | По умолчанию | Описание |
|-------|-----|--------------|----------|
| `selectCount` | `Int` | `9` | `1` = одиночный, `>1` = множественный; авто-clamp к `1..9` |
| `mediaType` | `MediaType` | `IMAGE` | `IMAGE` / `VIDEO` / `ALL` |
| `spanCount` | `Int` | `3` | Столбцы сетки; авто-clamp к **2–6** |
| `showCamera` | `Boolean` | `true` | Показать плитку камеры в первой ячейке; снимки сохраняются в `DCIM/Camera` (см. [Съёмка на камеру](#съёмка-на-камеру)) |
| `minImageSize` | `Long` | `0` | Мин. размер файла изображения (байты); фильтрует мелкие иконки. Только изображения |
| `maxImageSize` | `Long` | `Long.MAX_VALUE` | Макс. размер файла изображения (байты); фильтрует слишком большие. Только изображения |
| `minVideoDuration` | `Long` | `0` | Мин. длительность видео (мс); авто-обмен если > maxVideoDuration |
| `maxVideoDuration` | `Long` | `60000` | Макс. длительность видео (мс); авто-обмен если < minVideoDuration |
| `themeMode` | `ThemeMode` | `FOLLOW_SYSTEM` | `LIGHT` / `DARK` / `FOLLOW_SYSTEM` (на Activity, не глобально) |
| `cropConfig` | `CropConfig` | см. ниже | Настройки обрезки |
| `compressConfig` | `CompressConfig` | см. ниже | Сжатие при завершении |

Собрать отдельно для Contract:

```kotlin
val config = PhotoChoice.with(context)
    .selectCount(1)
    .buildConfig()  // возвращает PhotoChoiceConfig напрямую
```

### Обрезка `CropConfig`

Только при **`selectCount = 1`** и **`mediaType` включает изображения** — открывает автономный `CropActivity`.
Обрезка автоматически отключается (тихая деградация) для режима только видео или множественного выбора.

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

При одиночном выборе + включённой обрезке выбор изображения сразу ведёт к обрезке, затем возвращает результат и закрывает пикер.

### Сжатие `CompressConfig`

При нажатии **Готово** масштабирует и сжимает **изображения** в JPEG перед callback; видео, GIF и Live Photo (режим сохранения live) не сжимаются. Motion Photo по умолчанию сохраняют live; переключите на статичное в предпросмотре перед сжатием.

**Стратегия по умолчанию (в духе WeChat Moments):**

| Параметр | По умолчанию | Описание |
|----------|--------------|----------|
| `maxWidth` / `maxHeight` | `1280` | Лимит длинной стороны |
| `quality` | `80` | Начальное качество JPEG |
| `maxFileSizeBytes` | `1572864` (~1,5 МБ) | При превышении качество снижается по шагам; `0` = без лимита размера |
| `minQuality` | `50` | Мин. качество при итерации размера |
| `qualityStep` | `10` | Шаг снижения качества на итерацию |

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

> **Примечание:** Вывод всегда JPEG. Прозрачные PNG/WebP получат чёрный фон после сжатия (как в WeChat и других крупных приложениях).

---

## Рецепты

### Несколько изображений (до 9)

```kotlin
PhotoChoice.with(activity)
    .selectCount(9)
    .mediaType(MediaType.IMAGE)
    .spanCount(4)
    .showCamera(true)
    .forResult(activity) { result -> /* ... */ }
```

### Аватар (одиночный + квадратная обрезка)

```kotlin
PhotoChoice.with(activity)
    .selectCount(1)
    .mediaType(MediaType.IMAGE)
    .cropConfig(CropConfig(enabled = true, aspectRatio = CropAspectRatio.SQUARE))
    .compressConfig(CompressConfig(enabled = true))
    .forResult(activity) { result -> /* ... */ }
```

### Только видео (макс. 60 с)

```kotlin
PhotoChoice.with(activity)
    .selectCount(1)
    .mediaType(MediaType.VIDEO)
    .showCamera(false)
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }
```

### Изображения + видео

```kotlin
PhotoChoice.with(activity)
    .selectCount(9)
    .mediaType(MediaType.ALL)
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }
```

---

## Пример приложения

Модуль **`sample`** демонстрирует все опции:

```bash
./gradlew :sample:installDebug
```

Запустите **PhotoChoice Sample**, настройте параметры, откройте пикер и просмотрите выбранные медиа из списка результатов.

---

## Архитектура и производительность

### Пагинация

**Paging 3 + keyset MediaStore** (`DATE_ADDED` + `_ID`) — без полного сканирования Cursor:

| Параметр | Пример (`spanCount = 3`) |
|----------|--------------------------|
| Начальная загрузка | ~15 строк × столбцы ≈ 45 элементов |
| Размер страницы | ~25 строк × столбцы ≈ 75 элементов |
| Расстояние prefetch | ~35 строк × столбцы ≈ 105 элементов (~3 экрана) |
| Лимит памяти | ~900–1200 элементов метаданных (отбрасывает дальние страницы) |

`load` страницы **не выполняет разбор XMP** — холодный старт и быстрая прокрутка остаются плавными.

### Pipeline Motion Photo

```
Загрузка страницы MediaStore
    ├─ Sync: API 34+ batch IS_MOTION_PHOTO → MediaFile.isMotionPhoto
    └─ Async (неблокирующий):
           ├─ Открытие альбома: warmAlbumFromMediaStore
           ├─ Канал viewport: видимый + prefetch, высокоприоритетный XMP sniff
           └─ Фоновый канал: окно prefetch низкого приоритета
```

Модули в `data/motion/`: `MotionPhotoDetector`, `MotionPhotoListEnricher`, `MotionPhotoXmpSniffer`, `MotionPhotoVideoResolver`.

### Ключевые зависимости

- **Glide** — миниатюры и изображения предпросмотра
- **Paging 3** — пагинация сетки
- **Media3 ExoPlayer** — воспроизведение видео / Motion Photo в предпросмотре
- **ViewPager2** — пагинация предпросмотра

---

## Безопасность конфигурации

PhotoChoice применяет **защитную нормализацию** ко всем пользовательским значениям конфигурации, чтобы неверный ввод никогда не вызывал сбой библиотеки:

| Поле | Нормализация |
|------|--------------|
| `selectCount` | clamp к `1..9`; вне диапазона возвращает `1` |
| `spanCount` | clamp к `2..6` |
| `minVideoDurationMs` / `maxVideoDurationMs` | авто-обмен если min > max; min clamp к `>= 0` |
| `minImageSize` / `maxImageSize` | авто-обмен если min > max; min clamp к `>= 0` |
| `cropConfig.enabled` | авто-отключение для режима VIDEO или множественного выбора (`effectiveCropEnabled`) |

---

## Структура проекта

```
photo_choice/
├── photo-choice/              # Библиотека (публичный API: PhotoChoice)
│   └── src/main/java/com/google/photochoice/
│       ├── PhotoChoice.kt     # Точка входа Builder, forResult()
│       ├── PhotoChoiceContract.kt     # ActivityResultContract (рекомендуется)
│       ├── config/
│       ├── data/
│       │   └── motion/        # Обнаружение Motion Photo, XMP, извлечение видео
│       ├── viewmodel/
│       └── ui/
│           ├── grid/
│           ├── album/
│           ├── crop/          # CropActivity
│           └── preview/       # PreviewActivity, long-press live playback
├── sample/
├── docs/
│   ├── demo.mp4               # README demo video
│   ├── demo-cover.jpg         # Demo cover frame
│   └── sample-apk-qr.png      # Sample APK download QR
├── CHANGELOG.md               # История изменений
├── README.md                  # English documentation
├── README.zh-CN.md            # 简体中文文档
├── README.ja.md               # 日本語ドキュメント
├── README.ko.md               # 한국어 문서
├── README.fr.md               # Documentation en français
├── README.es.md               # Documentación en español
├── README.ar.md               # الوثائق العربية
└── README.ru.md               # Этот документ (русский)
```

---

## Сборка и проверка

```bash
./gradlew :photo-choice:assembleDebug
./gradlew :sample:installDebug
./gradlew test
./gradlew lint
```

---

## Чеклист интеграции

- [ ] `implementation(project(":photo-choice"))` (или эквивалент Maven)
- [ ] Разрешения на чтение медиа в Manifest хоста
- [ ] Runtime-разрешение перед запуском (`PermissionHelper`)
- [ ] Выбор API запуска: **`PhotoChoiceContract`** (рекомендуется, устойчив к process death) или callback `forResult`
- [ ] Обработка `null` (отмена) vs `PhotoChoiceResult` (успех)
- [ ] Вызов `PhotoChoice.cleanup(context)` при использовании сжатия/обрезки
- [ ] Для Live Photo + сжатие понимать **Сохранить live / Экспортировать статичное** в предпросмотре

---

## Ограничения

- Источник данных — **только публичные медиа MediaStore**, не приватные/скрытые папки.
- Цвета UI и акцента не настраиваются; только `ThemeMode` светлая/тёмная/система.
- **Не запускайте** `PhotoChoiceActivity`, `PreviewActivity` или `CropActivity` напрямую.
- Фильтры длительности видео влияют только на список, не на файлы на диске.
- **Значки LIVE**:
  - Почти мгновенно при API 34+ и `IS_MOTION_PHOTO` в MediaStore.
  - Краткая задержка на OEM без флагов DB (асинхронный XMP sniff при первом входе в viewport).
  - Долгое нажатие в предпросмотре всё ещё обнаруживает немаркированные motion photos через полное обнаружение (вкл. XMP).

---

## Проблемы

Укажите **версию Android, модель устройства, фрагмент конфигурации, ожидаемое vs фактическое поведение**. Для багов Motion Photo укажите, распознаёт ли системная галерея элемент как live/Motion Photo.
