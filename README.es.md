<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://github.com/Hu12037102/photo_choice/raw/master/docs/hero-dark.png">
    <img src="docs/hero-light.png" width="860" alt="PhotoChoice — Selector de fotos para Android: cuadrícula, álbumes, vista previa a pantalla completa, recorte, compresión, Motion Photo">
  </picture>
</p>

<p align="center">
  <a href="https://jitpack.io/#Hu12037102/photo_choice"><img src="https://img.shields.io/jitpack/version/com.github.Hu12037102/photo_choice?style=flat-square&label=JitPack&color=C8763C" alt="JitPack"></a>
  <img src="https://img.shields.io/badge/minSdk-29-1D1D1F?style=flat-square" alt="minSdk 29">
  <img src="https://img.shields.io/badge/language-Kotlin-1D1D1F?style=flat-square" alt="Kotlin">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-1D1D1F?style=flat-square" alt="Apache 2.0"></a>
</p>

<p align="center">
  <sub>
    <a href="README.md">English</a> ·
    <a href="README.zh-CN.md">简体中文</a> ·
    <a href="README.ja.md">日本語</a> ·
    <a href="README.ko.md">한국어</a> ·
    <a href="README.fr.md">Français</a> ·
    <a href="README.ar.md">العربية</a> ·
    <a href="README.ru.md">Русский</a>
  </sub>
</p>

<br>

Biblioteca de selector de fotos para Android: cuadrícula de selección múltiple, cambio de álbum,
vista previa a pantalla completa, mosaico de cámara opcional, recorte de imagen única, compresión
opcional y detección de **Motion Photo / Live Photo** con reproducción en la vista previa. La
integración se hace a través de una **API Builder**, nunca lanzando directamente las Activity
internas de la biblioteca.

<br>

## Demo

<p align="center">
  <video src="https://github.com/Hu12037102/photo_choice/raw/master/docs/demo.mp4" poster="https://github.com/Hu12037102/photo_choice/raw/master/docs/demo-poster.png" width="820" controls muted playsinline>
    <a href="https://github.com/Hu12037102/photo_choice/raw/master/docs/demo.mp4">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://github.com/Hu12037102/photo_choice/raw/master/docs/demo-poster-dark.png">
        <img src="docs/demo-poster.png" width="820" alt="Ver la demostración de PhotoChoice">
      </picture>
    </a>
  </video>
</p>

<p align="center">
  <sub>Cuadrícula y álbumes · orden de selección · fecha al desplazar · mosaico de cámara · vista previa a pantalla completa<br>
  reproducción de vídeo · Motion Photo · recorte · compresión JPEG · claro / oscuro / sistema</sub>
</p>

<br>

<p align="center">
  <a href="https://huxiaobai.oss-cn-shanghai.aliyuncs.com/open/sample-release.apk"><img src="docs/qr-sample-apk.png" width="200" alt="Escanee para instalar la app de ejemplo de PhotoChoice"></a>
</p>

<p align="center">
  <b><a href="https://huxiaobai.oss-cn-shanghai.aliyuncs.com/open/sample-release.apk">Descargar la app de ejemplo</a></b><br>
  <sub>Escanee con su teléfono, o toque para descargar · <code>sample-release.apk</code> · Android 10+</sub>
</p>

---

## Aspectos destacados

| Área | Qué ofrece |
|------|------------|
| Tipos de medios | Solo imágenes / solo vídeos / imágenes + vídeos |
| Selección | Simple o múltiple (`selectCount` 1–9), con distintivos de orden de selección |
| Álbumes | Agregación de buckets de MediaStore con selector desplegable |
| Cuadrícula | Número de columnas configurable (2–6), miniaturas cuadradas, Paging 3 |
| Cabecera de fecha | Muestra la fecha de la zona visible durante el desplazamiento |
| Cámara | Mosaico de cámara opcional en la primera celda; las fotos van a `DCIM/Camera` |
| Vista previa | Deslizamiento a pantalla completa, reproducción de vídeo integrada |
| Motion Photo | Distintivo LIVE en la cuadrícula; pulsación larga en la vista previa para reproducir el clip incrustado |
| Recorte | Selección simple + modo imagen; `CropActivity` independiente |
| Compresión | Redimensionado y calidad JPEG al finalizar, con bucle de reintento hacia un tamaño objetivo |
| Tema | Claro / oscuro / seguir al sistema, aplicado por Activity — nunca reescribe el modo global de la app anfitriona |
| API de lanzamiento | **`PhotoChoiceContract`** (recomendada, sin estado estático) o el callback `forResult` |
| Resistencia a la muerte del proceso | El modo Contract sobrevive a la recreación de Activity y a la muerte del proceso |

- **Paquete** `com.google.photochoice` · **Versión** `1.1.0` ([CHANGELOG](CHANGELOG.md))
- **minSdk** 29 (Android 10, Scoped Storage — lectura de medios públicos sin permiso de escritura heredado)
- **compileSdk** 36 · **Java** 11 · **Kotlin** · [Apache License 2.0](LICENSE)

---

## Instalación

### Opción A — JitPack (recomendada)

Añada el repositorio JitPack al **`settings.gradle.kts`** del anfitrión. Este proyecto usa
`FAIL_ON_PROJECT_REPOS`, así que el repositorio debe ir en `dependencyResolutionManagement`, no en
el módulo:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Después declare la dependencia en su módulo de aplicación o de funcionalidad:

```kotlin
dependencies {
    implementation("com.github.Hu12037102:photo_choice:1.1.0")
}
```

> JitPack compila el AAR bajo demanda a partir del código etiquetado; la primera petición de una
> etiqueta nueva puede tardar un minuto.

### Opción B — módulo de código fuente

```kotlin
// settings.gradle.kts
include(":photo-choice")

// app/build.gradle.kts
dependencies {
    implementation(project(":photo-choice"))
}
```

---

## Inicio rápido

### 1. Declarar los permisos

La biblioteca declara los permisos de lectura de medios en su propio Manifest, pero **la app
anfitriona debe declarar los mismos permisos** y solicitarlos en tiempo de ejecución.

| Versión de Android | Permisos |
|--------------------|----------|
| API 34+ | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_VISUAL_USER_SELECTED` — una concesión parcial se considera utilizable |
| API 33 | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` |
| API 29–32 | `READ_EXTERNAL_STORAGE` |

`PermissionHelper` proporciona la lista y la comprobación de concesión:

```kotlin
import com.google.photochoice.util.PermissionHelper

if (PermissionHelper.hasMediaPermission(context)) {
    openPhotoChoice()
} else {
    requestPermissionLauncher.launch(PermissionHelper.requiredMediaPermissions())
}
```

`requiredMediaPermissions()` devuelve el conjunto **completo** para el nivel de SDK en ejecución;
**no** lo reduce en función de su `mediaType`. En API 34+, `hasMediaPermission()` devuelve `true` si
se concedió **cualquiera** de los tres (el acceso parcial a fotos cuenta); en API 33 requiere
**ambos** permisos, de imagen y de vídeo.

### 2. Lanzar el selector — Contract (recomendado)

```kotlin
import com.google.photochoice.PhotoChoice
import com.google.photochoice.PhotoChoiceContract
import com.google.photochoice.config.MediaType

val launcher = registerForActivityResult(PhotoChoiceContract()) { result ->
    if (result == null) return@registerForActivityResult   // cancelado
    result.uris.forEach { uri ->
        // URI content:// o file://, en orden de selección
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

`PhotoChoiceContract` es un `ActivityResultContract<PhotoChoiceConfig, PhotoChoiceResult?>`. La
configuración viaja como extra del Intent y el resultado vuelve mediante `setResult()`: ambos los
gestiona el sistema, de modo que esto sobrevive a la recreación de la Activity y a la muerte del
proceso sin ningún estado estático. **Es lo recomendable para todo uso en producción.**

### 3. Alternativa — API de callback (heredada)

Desde una `FragmentActivity` (o `AppCompatActivity`):

```kotlin
PhotoChoice.with(this)
    .selectCount(9)
    .mediaType(MediaType.IMAGE)
    .forResult(this) { result ->
        if (result == null) return@forResult   // cancelado
        result.uris.forEach { uri -> /* ... */ }
    }
```

> **La API de callback guarda el callback en un campo estático.** Por eso no sobrevive a la
> recreación de la Activity anfitriona ni a la muerte del proceso: si el anfitrión muere mientras el
> selector está en marcha, el callback se pierde y el selector se cierra limpiamente sin resultado.
> Use el Contract anterior cuando eso importe.

---

## Configuración

Cada setter devuelve el `Builder`. Las llamadas terminales son `buildConfig()` (para
`PhotoChoiceContract`), `forResult(activity, callback)`, o `build()` si quiere la propia instancia
de `PhotoChoice`.

| Método | Tipo | Por defecto | Notas |
|--------|------|-------------|-------|
| `selectCount` | `Int` | `9` | `1` = simple, `>1` = múltiple. Un valor fuera de `1..9` **vuelve a `1`**, no se ajusta al límite más cercano |
| `mediaType` | `MediaType` | `IMAGE` | `IMAGE` / `VIDEO` / `ALL` |
| `spanCount` | `Int` | `3` | Columnas de la cuadrícula, acotado a `2..6` |
| `showCamera` | `Boolean` | `true` | Mosaico de cámara en la primera celda — véase [Captura con la cámara](#captura-con-la-cámara) |
| `minImageSize` | `Long` | `0` | Tamaño mínimo del archivo de imagen en bytes; filtra iconos pequeños. Solo imágenes |
| `maxImageSize` | `Long` | `Long.MAX_VALUE` | Tamaño máximo del archivo de imagen en bytes. Solo imágenes |
| `minVideoDuration` | `Long` | `0` | Duración mínima de vídeo en ms |
| `maxVideoDuration` | `Long` | `60_000` | Duración máxima de vídeo en ms |
| `themeMode` | `ThemeMode` | `FOLLOW_SYSTEM` | `LIGHT` / `DARK` / `FOLLOW_SYSTEM`, aplicado por Activity |
| `cropConfig` | `CropConfig` | `CropConfig()` | Véase más abajo |
| `compressConfig` | `CompressConfig` | `CompressConfig()` | Véase más abajo |

> **`spanCount` tiene dos valores por defecto distintos.** El `Builder` usa `3`, pero el parámetro
> del constructor de `PhotoChoiceConfig` vale `4` por defecto. Si construye un `PhotoChoiceConfig`
> directamente en lugar de pasar por el Builder, obtiene 4 columnas.

`PhotoChoice.with(context)` ignora actualmente su argumento `context`: se mantiene por
compatibilidad de la API y por un punto de llamada natural.

### Recorte — `CropConfig`

```kotlin
import com.google.photochoice.config.CropConfig
import com.google.photochoice.config.CropAspectRatio

.cropConfig(
    CropConfig(
        enabled = true,
        aspectRatio = CropAspectRatio.SQUARE,
        maxWidth = 0,      // 0 = sin límite
        maxHeight = 0,     // 0 = sin límite
    )
)
```

| Campo | Por defecto | Notas |
|-------|-------------|-------|
| `enabled` | `false` | Abre la `CropActivity` independiente tras la selección |
| `aspectRatio` | `ORIGINAL` | `ORIGINAL` / `SQUARE` / `RATIO_3_4` / `RATIO_4_3` / `RATIO_9_16` / `RATIO_16_9`; cada constante expone un `ratio: Float?` (`null` para `ORIGINAL`) |
| `maxWidth` | `0` | Limita el ancho de salida en píxeles; `0` o menos significa sin límite |
| `maxHeight` | `0` | Limita el alto de salida en píxeles; `0` o menos significa sin límite |

El recorte solo se activa si `selectCount == 1` **y** `mediaType == MediaType.IMAGE`.

> **`MediaType.ALL` desactiva el recorte de forma silenciosa.** La comprobación es una igualdad
> estricta con `IMAGE`, no «incluye imágenes», de modo que un selector mixto de imágenes y vídeos
> nunca llega a la pantalla de recorte, ni siquiera con `enabled = true`.

Con selección simple y recorte activado, elegir una imagen lleva directamente al recorte y luego
cierra el selector.

### Compresión — `CompressConfig`

Al pulsar **Hecho**, las imágenes se escalan y comprimen en JPEG antes de entregar el resultado. Los
vídeos, los GIF y las Motion Photos en modo «conservar movimiento» nunca se comprimen.

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
        qualityStep = 10,
    )
)
```

| Campo | Por defecto | Notas |
|-------|-------------|-------|
| `enabled` | `false` | Interruptor principal |
| `maxWidth` / `maxHeight` | `1280` | Límites del lado más largo para el redimensionado |
| `quality` | `80` | Calidad JPEG inicial, acotada a `1..100` en el momento de uso |
| `maxFileSizeBytes` | `1_572_864` (~1,5 MB) | Tamaño de salida objetivo; la calidad baja por pasos hasta encajar |
| `minQuality` | `50` | Suelo de ese bucle de reintento — nunca baja de ahí |
| `qualityStep` | `10` | Cuánto cae la calidad en cada reintento |
| `skipCompressBaselineLongEdge` | `1280` | Umbral para omitir, lado largo |
| `skipCompressBaselineShortEdge` | `720` | Umbral para omitir, lado corto |
| `skipCompressMaxBytes` | `153_600` (150 KB) | Umbral para omitir, tamaño de archivo |

**Una imagen que ya es lo bastante pequeña se devuelve sin tocar:** lado largo ≤ 1280 **y** lado
corto ≤ 720, **o** archivo por debajo de 150 KB. Recomprimirlas solo perdería calidad sin un ahorro
apreciable. Las Motion Photos exportadas como imagen estática esquivan deliberadamente esta
exención y siempre se comprimen.

> La salida siempre es JPEG. Un PNG o WebP transparente vuelve con fondo negro.

---

## Resultado

```kotlin
data class PhotoChoiceResult(
    val uris: List<Uri>,    // URI seleccionados, en orden de selección
    val paths: List<String> // rutas locales en la medida de lo posible; la cadena del URI si no se resuelve
)
```

`paths` solo contiene rutas reales del sistema de archivos para los archivos que produjo la propia
biblioteca (salida comprimida o recortada). Los elementos de MediaStore devuelven su URI
`content://` como cadena.

| Medio | Sin compresión | Con compresión |
|-------|----------------|----------------|
| Imagen estática | URI `content://` de MediaStore | JPEG `file://` en `cacheDir/photo_choice/compress_<uuid>.jpg` |
| Imagen pequeña (por debajo del umbral) | URI `content://` de MediaStore | `content://` — sin tocar |
| Vídeo | URI `content://` de MediaStore | Sin tocar |
| GIF | URI `content://` de MediaStore | Sin tocar (la compresión perdería la animación) |
| Live Photo — conservar movimiento | URI `content://` de MediaStore | Sin tocar (movimiento preservado) |
| Live Photo — exportar como estática | n/d | JPEG `file://` comprimido |
| Imagen recortada | `file://` en `cacheDir/photo_choice/crop_<timestamp>.jpg` | igual, y luego comprimida |

### Limpieza

```kotlin
PhotoChoice.cleanup(context)
```

> **Esto borra todo, no solo los archivos antiguos.** `cleanup()` vacía sin condiciones
> `cacheDir/photo_choice/`, `cacheDir/photo_choice_motion/` y `cacheDir/photo_choice_camera/`, y
> descarta la caché en memoria de Motion Photo. Llámelo **después** de haber consumido el resultado:
> un URI `file://` que todavía conserve dejaría de resolverse.
>
> El barrido por antigüedad de 24 horas es una rutina interna aparte que la biblioteca ejecuta por su
> cuenta; no necesita programarla.

---

## Recetas

```kotlin
// Varias imágenes, hasta 9
PhotoChoice.with(activity)
    .selectCount(9)
    .mediaType(MediaType.IMAGE)
    .spanCount(4)
    .showCamera(true)
    .forResult(activity) { result -> /* ... */ }

// Avatar: selección simple + recorte cuadrado + compresión
PhotoChoice.with(activity)
    .selectCount(1)
    .mediaType(MediaType.IMAGE)
    .cropConfig(CropConfig(enabled = true, aspectRatio = CropAspectRatio.SQUARE))
    .compressConfig(CompressConfig(enabled = true))
    .forResult(activity) { result -> /* ... */ }

// Solo vídeo, máx. 60 s
PhotoChoice.with(activity)
    .selectCount(1)
    .mediaType(MediaType.VIDEO)
    .showCamera(false)          // en modo VIDEO se oculta automáticamente de todos modos
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }

// Imágenes + vídeos — tenga en cuenta que el recorte no está disponible en modo ALL
PhotoChoice.with(activity)
    .selectCount(9)
    .mediaType(MediaType.ALL)
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }
```

---

## Comportamiento en detalle

### Selección simple frente a múltiple

| Modo | Interfaz de la cuadrícula | Interacción |
|------|---------------------------|-------------|
| Múltiple (`selectCount > 1`) | Casilla + distintivo de orden | Toque la casilla para alternar; toque la miniatura para la vista previa |
| Simple (`selectCount == 1`) | **Oculta** la casilla, el distintivo de orden y la capa de desactivado | Toque la miniatura → vista previa, o recorte si está activado |

La selección simple no tiene un estado intermedio de «seleccionado», y por eso los elementos de
interfaz de selección desaparecen por completo en lugar de quedar desactivados.

### Captura con la cámara

Con `showCamera(true)` (el valor por defecto), la primera celda de la cuadrícula es una entrada de
cámara.

| Elemento | Valor |
|----------|-------|
| Directorio | `DCIM/Camera` — el directorio público de la cámara, es decir, el álbum «Cámara» del sistema |
| Nombre de archivo | `IMG` + los últimos 8 dígitos de la marca de tiempo + 4 dígitos aleatorios + `.jpg`, p. ej. `IMG064001234821.jpg` |
| Formato | JPEG |
| Área temporal | `cacheDir/photo_choice_camera/`, que limpia el limpiador de sandbox |

Las fotos se insertan mediante el protocolo en dos fases `IS_PENDING` de MediaStore: la fila solo se
hace visible para la galería del sistema una vez escritos todos los bytes, de modo que ninguna otra
app escanea un archivo parcial. Si la copia falla, la fila pendiente se elimina en lugar de quedar
huérfana.

**Qué debe hacer la app anfitriona: nada.** La biblioteca declara su propio `FileProvider` con la
autoridad `${applicationId}.photochoice.fileprovider`, derivada del `applicationId` del anfitrión,
por lo que nunca puede chocar con otro integrador. Tampoco hace falta permiso de cámara: la captura
va por `ACTION_IMAGE_CAPTURE` y es la app de cámara la que tiene el permiso.

> Si no hay ninguna app de cámara instalada, tocar el mosaico muestra un mensaje en lugar de fallar.
>
> Si su app declara `<uses-permission android:name="android.permission.CAMERA" />` en su propio
> Manifest, Android exigirá que ese permiso esté concedido antes de poder usar el intent. Es una
> regla de la plataforma, no un requisito de la biblioteca.

Después de una captura:

| Modo | Comportamiento |
|------|----------------|
| Múltiple | La foto se selecciona automáticamente. Si ya se alcanzó `selectCount`, aparece un mensaje de límite y la foto permanece en la galería |
| Simple + recorte activado | Va directo a la pantalla de recorte; cancelar refresca la lista y la foto sigue visible en la cuadrícula |
| Simple + recorte desactivado | Solo refresca la lista y los datos de álbum — sin selección automática |

El álbum que el usuario está viendo nunca cambia; solo se refrescan la lista y los agregados de
álbumes. Si ese álbum no es «Cámara», la foto nueva aparece al cambiar a él.

Cuando `mediaType` es `VIDEO`, el mosaico de cámara se oculta automáticamente
(`effectiveShowCamera`): una foto estática nunca podría aparecer en una lista solo de vídeo, así que
el punto de entrada no se ofrece.

### Motion Photo / Live Photo

La biblioteca trata las **Motion Photo, Google Motion Photo, fotos en movimiento de Samsung** y
archivos JPEG/HEIC similares con un vídeo corto incrustado como motion photos. Siguen siendo de tipo
`IMAGE` en todo el recorrido.

**En la cuadrícula**

- Un distintivo **LIVE** aparece abajo a la izquierda de la miniatura.
- **La paginación nunca se bloquea.** El `load` de una página solo lee `IS_MOTION_PHOTO` de
  MediaStore de forma síncrona (API 34+); el análisis XMP corre de forma asíncrona.
- **El índice es persistente.** Los resultados del escaneo sobreviven a los cambios de configuración
  y a la muerte del proceso, así que no se vuelve a analizar en cada apertura.
- **La zona visible tiene prioridad.** Un canal de análisis de alta prioridad dedicado cubre la zona
  visible y la ventana de precarga, de modo que un desplazamiento rápido no queda atascado tras una
  cola de todo el historial.
- En dispositivos OEM que no exponen `IS_MOTION_PHOTO` —algo común en ciertos modelos— los
  distintivos dependen del análisis XMP asíncrono de cabecera/cola, por lo que su primera aparición
  en pantalla puede retrasarse ligeramente, normalmente por debajo de unos cientos de milisegundos.

**En la vista previa a pantalla completa**

- El distintivo LIVE se sitúa bajo la barra superior.
- **Pulsación larga** para reproducir el vídeo incrustado, **soltar** para detenerlo. Pellizcar y
  hacer zoom no detienen la reproducción por accidente.
- Al entrar, el MP4 incrustado se detecta y precarga en segundo plano, con caché en
  `cacheDir/photo_choice_motion/`.

**Con la compresión activada**, la vista previa ofrece una elección:

- **Conservar movimiento** (por defecto) — devuelve el URI original, sin compresión.
- **Exportar como estática** — compresión JPEG, movimiento descartado.

---

## Arquitectura y rendimiento

### Paginación

**Paging 3 sobre un keyset de MediaStore** (`DATE_ADDED` + `_ID`) — sin recorrido completo del cursor.

| Parámetro | Valor |
|-----------|-------|
| Carga inicial | 500 elementos fijos, redondeados hacia arriba hasta una fila completa |
| Tamaño de página | `spanCount × 25` elementos |
| Distancia de precarga | `spanCount × 35` elementos (~3 pantallas) |
| Tope de memoria | **Ninguno.** `maxSize` no se establece deliberadamente |

`maxSize` se eliminó a propósito: descartar las páginas más lejanas rompía el rellenado de páginas y
falseaba los totales de la vista previa. El `load` de una página no ejecuta ningún análisis XMP, que
es lo que mantiene fluidos el arranque en frío y el desplazamiento rápido.

### Pipeline de Motion Photo

```
Carga de página de MediaStore
    ├─ Síncrono: lote IS_MOTION_PHOTO en API 34+ → MediaFile.isMotionPhoto
    └─ Asíncrono (no bloqueante):
           ├─ Apertura de álbum: warmAlbumFromMediaStore
           ├─ Canal de zona visible: visible + precarga, análisis XMP de alta prioridad
           └─ Canal de segundo plano: ventana de precarga de baja prioridad
```

Implementado bajo `data/motion/`: `MotionPhotoDetector`, `MotionPhotoListEnricher`,
`MotionPhotoXmpSniffer`, `MotionPhotoVideoResolver`.

### Directorios del sandbox

| Directorio | Contenido | Retención |
|------------|-----------|-----------|
| `cacheDir/photo_choice/` | Salidas comprimidas y recortadas | Barrido de 24 h; vaciado por `cleanup()` |
| `cacheDir/photo_choice_motion/` | Clips de Motion Photo extraídos | Barrido de 24 h, más topes de 150 MB / 50 archivos |
| `cacheDir/photo_choice_camera/` | Archivos temporales de captura | Borrados tras cada captura; barrido de 24 h como red de seguridad |

### Dependencias clave

**Glide** para miniaturas e imágenes de vista previa · **Paging 3** para la cuadrícula ·
**Media3 ExoPlayer** para la reproducción de vídeo y Motion Photo · **ViewPager2** para la
paginación de la vista previa.

---

## Superficie de API pública

Solo estos tipos constituyen la API soportada y a prueba de ofuscación — son los que conserva
`consumer-rules.pro`:

`PhotoChoice` · `PhotoChoice.Builder` · `PhotoChoiceContract` · `PhotoChoiceResult` ·
todo lo que hay bajo `config.**`

Las demás clases son públicas según la visibilidad de Kotlin y por tanto invocables
(`CameraHelper`, `CompressHelper`, `SandboxCleaner`, `DesignTokens` y compañía), pero son **detalles
internos de implementación**: no están cubiertas por el versionado semántico y pueden cambiar o
desaparecer en cualquier versión. `PermissionHelper` es la única excepción: está documentada arriba
y pensada para su uso desde el anfitrión.

Nunca lance directamente `PhotoChoiceActivity`, `PreviewActivity` ni `CropActivity`.

### Seguridad de la configuración

Las entradas no válidas se sanean en lugar de lanzar excepciones, de modo que una configuración
errónea nunca puede hacer fallar la biblioteca:

| Campo | Regla |
|-------|-------|
| `selectCount` | Se conserva si está dentro de `1..9`; en caso contrario, **se restablece a `1`** |
| `spanCount` | Acotado a `2..6` |
| `minVideoDurationMs` / `maxVideoDurationMs` | Se intercambian si min > max; min con suelo en `0` |
| `minImageSize` / `maxImageSize` | Se intercambian si min > max; ambos con suelo en `0` |
| `cropConfig.enabled` | Requiere selección simple **y** `MediaType.IMAGE` (`effectiveCropEnabled`) |
| `showCamera` | Forzado a off en modo `MediaType.VIDEO` (`effectiveShowCamera`) |

`PhotoChoiceConfig` expone los límites como constantes — `SELECT_COUNT_MIN` / `SELECT_COUNT_MAX`,
`SPAN_COUNT_MIN` / `SPAN_COUNT_MAX` — junto con las propiedades derivadas `sanitized*` y
`effective*`, por si quiere reflejar los valores efectivos en su propia interfaz.

---

## Estructura del proyecto

```
photo_choice/
├── photo-choice/                    # la biblioteca
│   └── src/main/java/com/google/photochoice/
│       ├── PhotoChoice.kt           # punto de entrada del Builder, forResult()
│       ├── PhotoChoiceContract.kt   # ActivityResultContract (recomendado)
│       ├── PhotoChoiceResult.kt
│       ├── config/                  # PhotoChoiceConfig, MediaType, ThemeMode, Crop/CompressConfig
│       ├── data/
│       │   ├── model/
│       │   └── motion/              # detección de Motion Photo, análisis XMP, extracción del clip
│       ├── ui/
│       │   ├── grid/
│       │   ├── album/
│       │   ├── crop/                # CropActivity
│       │   ├── preview/             # PreviewActivity, reproducción live con pulsación larga
│       │   └── widget/
│       ├── util/                    # PermissionHelper, CameraHelper, CompressHelper, SandboxCleaner
│       └── viewmodel/
├── sample/                          # app de demostración que cubre todas las opciones
├── docs/
│   ├── demo.mp4                     # vídeo de demostración
│   ├── demo-poster.png              # cartel del vídeo (claro / oscuro)
│   ├── hero-light.png               # cabecera del README (claro / oscuro)
│   ├── qr-sample-apk.png            # código QR del APK de ejemplo
│   └── assets/make_assets.py        # regenera todas las imágenes anteriores
├── CHANGELOG.md
└── README.md                        # más 7 traducciones
```

### Build y verificación

```bash
./gradlew :photo-choice:assembleDebug
./gradlew :sample:installDebug
./gradlew test
./gradlew lint
```

Para regenerar las imágenes del README tras un cambio de paleta o de texto:

```bash
python docs/assets/make_assets.py
```

---

## Lista de verificación de integración

- [ ] Dependencia añadida — JitPack o `implementation(project(":photo-choice"))`
- [ ] Permisos de lectura de medios declarados en el Manifest del anfitrión
- [ ] Permiso en tiempo de ejecución solicitado antes del lanzamiento, mediante `PermissionHelper`
- [ ] API de lanzamiento elegida — **`PhotoChoiceContract`** (resistente a la muerte del proceso) o el callback `forResult`
- [ ] `null` (cancelado) tratado por separado de un `PhotoChoiceResult`
- [ ] `PhotoChoice.cleanup(context)` llamado **después** de consumir la salida de recorte/compresión
- [ ] Para Motion Photo + compresión, entendida la elección **Conservar movimiento / Exportar como estática**

---

## Limitaciones

- La fuente de datos son solo los **medios públicos de MediaStore**, no carpetas privadas u ocultas.
- La interfaz y los colores de acento no son personalizables; solo `ThemeMode` claro / oscuro / sistema.
- Los filtros de duración de vídeo afectan únicamente al listado, nunca a los archivos en disco.
- El recorte no está disponible en `MediaType.ALL` ni en selección múltiple.
- Los distintivos LIVE son casi instantáneos cuando `IS_MOTION_PHOTO` está establecido (API 34+),
  pero tardan un poco en dispositivos OEM sin ese campo en la base de datos. La pulsación larga en la
  vista previa sigue detectando motion photos sin marcar mediante una detección completa que incluye XMP.

## Problemas

Al abrir una incidencia, incluya la **versión de Android, el modelo del dispositivo, un fragmento de
configuración y el comportamiento esperado frente al observado**. Para errores de Motion Photo,
indique también si la galería del sistema reconoce el elemento como live.
