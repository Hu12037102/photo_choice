<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://github.com/Hu12037102/photo_choice/raw/master/docs/hero-dark.png">
    <img src="docs/hero-light.png" width="860" alt="PhotoChoice — Sélecteur de photos Android : grille, albums, aperçu plein écran, recadrage, compression, Motion Photo">
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
    <a href="README.es.md">Español</a> ·
    <a href="README.ar.md">العربية</a> ·
    <a href="README.ru.md">Русский</a>
  </sub>
</p>

<br>

Bibliothèque de sélecteur de photos pour Android : grille multi-sélection, changement d'album,
aperçu plein écran, tuile caméra optionnelle, recadrage d'image unique, compression optionnelle, et
détection **Motion Photo / Live Photo** avec lecture dans l'aperçu. L'intégration passe par une
**API Builder** — jamais par le lancement direct des Activity internes de la bibliothèque.

<br>

## Démo

<p align="center">
  <video src="https://github.com/Hu12037102/photo_choice/raw/master/docs/demo.mp4" poster="https://github.com/Hu12037102/photo_choice/raw/master/docs/demo-poster.png" width="820" controls muted playsinline>
    <a href="https://github.com/Hu12037102/photo_choice/raw/master/docs/demo.mp4">
      <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://github.com/Hu12037102/photo_choice/raw/master/docs/demo-poster-dark.png">
        <img src="docs/demo-poster.png" width="820" alt="Voir la démonstration de PhotoChoice">
      </picture>
    </a>
  </video>
</p>

<p align="center">
  <sub>Grille et albums · ordre de sélection · date au défilement · tuile caméra · aperçu plein écran<br>
  lecture vidéo · Motion Photo · recadrage · compression JPEG · clair / sombre / système</sub>
</p>

<br>

<p align="center">
  <a href="https://huxiaobai.oss-cn-shanghai.aliyuncs.com/open/sample-release.apk"><img src="docs/qr-sample-apk.png" width="200" alt="Scannez pour installer l'application d'exemple PhotoChoice"></a>
</p>

<p align="center">
  <b><a href="https://huxiaobai.oss-cn-shanghai.aliyuncs.com/open/sample-release.apk">Télécharger l'application d'exemple</a></b><br>
  <sub>Scannez avec votre téléphone, ou touchez pour télécharger · <code>sample-release.apk</code> · Android 10+</sub>
</p>

---

## Points clés

| Domaine | Ce que vous obtenez |
|---------|---------------------|
| Types de médias | Images seules / vidéos seules / images + vidéos |
| Sélection | Simple ou multiple (`selectCount` 1–9), avec badges d'ordre de sélection |
| Albums | Agrégation des buckets MediaStore avec sélecteur déroulant |
| Grille | Nombre de colonnes configurable (2–6), vignettes carrées, Paging 3 |
| En-tête de date | Affiche la date de la zone visible pendant le défilement |
| Caméra | Tuile caméra optionnelle en première cellule ; les photos vont dans `DCIM/Camera` |
| Aperçu | Balayage plein écran, lecture vidéo intégrée |
| Motion Photo | Badge LIVE dans la grille ; appui long dans l'aperçu pour lire le clip intégré |
| Recadrage | Sélection simple + mode image ; `CropActivity` autonome |
| Compression | Redimensionnement et qualité JPEG à la validation, avec boucle de reprise vers une taille cible |
| Thème | Clair / sombre / système, appliqué par Activity — ne réécrit jamais le mode global de l'application hôte |
| API de lancement | **`PhotoChoiceContract`** (recommandé, sans état statique) ou le callback `forResult` |
| Résistance à la mort du processus | Le mode Contract survit à la recréation d'Activity et à la mort du processus |

- **Package** `com.google.photochoice` · **Version** `1.1.0` ([CHANGELOG](CHANGELOG.md))
- **minSdk** 29 (Android 10, Scoped Storage — lecture des médias publics sans permission d'écriture héritée)
- **compileSdk** 36 · **Java** 11 · **Kotlin** · [Apache License 2.0](LICENSE)

---

## Installation

### Option A — JitPack (recommandé)

Ajoutez le dépôt JitPack dans le **`settings.gradle.kts`** de l'hôte. Ce projet utilise
`FAIL_ON_PROJECT_REPOS`, donc le dépôt doit figurer dans `dependencyResolutionManagement`, pas dans
le module :

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Déclarez ensuite la dépendance dans votre module applicatif ou fonctionnel :

```kotlin
dependencies {
    implementation("com.github.Hu12037102:photo_choice:1.1.0")
}
```

> JitPack construit l'AAR à la demande à partir des sources taguées ; la première requête pour un
> nouveau tag peut prendre une minute.

### Option B — module source

```kotlin
// settings.gradle.kts
include(":photo-choice")

// app/build.gradle.kts
dependencies {
    implementation(project(":photo-choice"))
}
```

---

## Démarrage rapide

### 1. Déclarer les permissions

La bibliothèque déclare les permissions de lecture des médias dans son propre Manifest, mais
**l'application hôte doit déclarer les mêmes permissions** et les demander à l'exécution.

| Version d'Android | Permissions |
|-------------------|-------------|
| API 34+ | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_VISUAL_USER_SELECTED` — un octroi partiel est considéré comme utilisable |
| API 33 | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` |
| API 29–32 | `READ_EXTERNAL_STORAGE` |

`PermissionHelper` fournit la liste et la vérification :

```kotlin
import com.google.photochoice.util.PermissionHelper

if (PermissionHelper.hasMediaPermission(context)) {
    openPhotoChoice()
} else {
    requestPermissionLauncher.launch(PermissionHelper.requiredMediaPermissions())
}
```

`requiredMediaPermissions()` renvoie l'ensemble **complet** correspondant au niveau de SDK courant ;
il ne restreint **pas** cet ensemble selon votre `mediaType`. Sur API 34+, `hasMediaPermission()`
renvoie `true` si **au moins une** des trois est accordée (l'accès photo partiel compte) ; sur
API 33, les permissions image **et** vidéo sont toutes deux requises.

### 2. Lancer le sélecteur — Contract (recommandé)

```kotlin
import com.google.photochoice.PhotoChoice
import com.google.photochoice.PhotoChoiceContract
import com.google.photochoice.config.MediaType

val launcher = registerForActivityResult(PhotoChoiceContract()) { result ->
    if (result == null) return@registerForActivityResult   // annulé
    result.uris.forEach { uri ->
        // URI content:// ou file://, dans l'ordre de sélection
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

`PhotoChoiceContract` est un `ActivityResultContract<PhotoChoiceConfig, PhotoChoiceResult?>`. La
configuration voyage dans un extra d'Intent et le résultat revient par `setResult()` : les deux sont
gérés par le système, donc cela survit à la recréation d'Activity et à la mort du processus sans le
moindre état statique. **À privilégier pour tout usage en production.**

### 3. Alternative — API callback (héritée)

Depuis une `FragmentActivity` (ou `AppCompatActivity`) :

```kotlin
PhotoChoice.with(this)
    .selectCount(9)
    .mediaType(MediaType.IMAGE)
    .forResult(this) { result ->
        if (result == null) return@forResult   // annulé
        result.uris.forEach { uri -> /* ... */ }
    }
```

> **L'API callback conserve le callback dans un champ statique.** Elle ne survit donc ni à la
> recréation de l'Activity hôte ni à la mort du processus : si l'hôte est tué pendant que le
> sélecteur tourne, le callback est perdu et le sélecteur se ferme proprement sans résultat.
> Utilisez le Contract ci-dessus quand cela compte.

---

## Configuration

Chaque setter renvoie le `Builder`. Les méthodes terminales sont `buildConfig()` (pour
`PhotoChoiceContract`), `forResult(activity, callback)`, ou `build()` si vous voulez l'instance
`PhotoChoice` elle-même.

| Méthode | Type | Défaut | Notes |
|---------|------|--------|-------|
| `selectCount` | `Int` | `9` | `1` = simple, `>1` = multiple. Une valeur hors de `1..9` **retombe à `1`** — elle n'est pas ramenée à la borne la plus proche |
| `mediaType` | `MediaType` | `IMAGE` | `IMAGE` / `VIDEO` / `ALL` |
| `spanCount` | `Int` | `3` | Colonnes de la grille, contraint dans `2..6` |
| `showCamera` | `Boolean` | `true` | Tuile caméra en première cellule — voir [Prise de photo](#prise-de-photo) |
| `minImageSize` | `Long` | `0` | Taille minimale du fichier image en octets ; filtre les petites icônes. Images uniquement |
| `maxImageSize` | `Long` | `Long.MAX_VALUE` | Taille maximale du fichier image en octets. Images uniquement |
| `minVideoDuration` | `Long` | `0` | Durée vidéo minimale en ms |
| `maxVideoDuration` | `Long` | `60_000` | Durée vidéo maximale en ms |
| `themeMode` | `ThemeMode` | `FOLLOW_SYSTEM` | `LIGHT` / `DARK` / `FOLLOW_SYSTEM`, appliqué par Activity |
| `cropConfig` | `CropConfig` | `CropConfig()` | Voir ci-dessous |
| `compressConfig` | `CompressConfig` | `CompressConfig()` | Voir ci-dessous |

> **`spanCount` a deux valeurs par défaut distinctes.** Le `Builder` utilise `3`, mais le paramètre
> du constructeur de `PhotoChoiceConfig` vaut `4` par défaut. Si vous construisez un
> `PhotoChoiceConfig` directement au lieu de passer par le Builder, vous obtenez 4 colonnes.

`PhotoChoice.with(context)` ignore actuellement son argument `context` : il est conservé pour la
compatibilité de l'API et pour un site d'appel naturel.

### Recadrage — `CropConfig`

```kotlin
import com.google.photochoice.config.CropConfig
import com.google.photochoice.config.CropAspectRatio

.cropConfig(
    CropConfig(
        enabled = true,
        aspectRatio = CropAspectRatio.SQUARE,
        maxWidth = 0,      // 0 = illimité
        maxHeight = 0,     // 0 = illimité
    )
)
```

| Champ | Défaut | Notes |
|-------|--------|-------|
| `enabled` | `false` | Ouvre la `CropActivity` autonome après la sélection |
| `aspectRatio` | `ORIGINAL` | `ORIGINAL` / `SQUARE` / `RATIO_3_4` / `RATIO_4_3` / `RATIO_9_16` / `RATIO_16_9` ; chaque constante expose un `ratio: Float?` (`null` pour `ORIGINAL`) |
| `maxWidth` | `0` | Plafonne la largeur de sortie en pixels ; `0` ou moins signifie illimité |
| `maxHeight` | `0` | Plafonne la hauteur de sortie en pixels ; `0` ou moins signifie illimité |

Le recadrage ne s'active que si `selectCount == 1` **et** `mediaType == MediaType.IMAGE`.

> **`MediaType.ALL` désactive silencieusement le recadrage.** Le test est une égalité stricte avec
> `IMAGE`, et non « contient des images » : un sélecteur mixte images + vidéos n'atteint donc jamais
> l'écran de recadrage, même avec `enabled = true`.

En sélection simple avec recadrage activé, choisir une image mène directement au recadrage, puis
referme le sélecteur.

### Compression — `CompressConfig`

À la validation (**Terminé**), les images sont redimensionnées et compressées en JPEG avant la
remise du résultat. Les vidéos, les GIF et les Motion Photos en mode « garder le mouvement » ne sont
jamais compressés.

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

| Champ | Défaut | Notes |
|-------|--------|-------|
| `enabled` | `false` | Interrupteur principal |
| `maxWidth` / `maxHeight` | `1280` | Bornes du plus grand côté pour le redimensionnement |
| `quality` | `80` | Qualité JPEG de départ, contrainte dans `1..100` à l'usage |
| `maxFileSizeBytes` | `1_572_864` (~1,5 Mo) | Taille de sortie visée ; la qualité baisse par paliers jusqu'à y tenir |
| `minQuality` | `50` | Plancher de cette boucle de reprise — jamais dépassé vers le bas |
| `qualityStep` | `10` | Baisse de qualité à chaque tentative |
| `skipCompressBaselineLongEdge` | `1280` | Seuil de contournement, grand côté |
| `skipCompressBaselineShortEdge` | `720` | Seuil de contournement, petit côté |
| `skipCompressMaxBytes` | `153_600` (150 Ko) | Seuil de contournement, taille de fichier |

**Une image déjà assez petite est renvoyée telle quelle :** grand côté ≤ 1280 **et** petit côté
≤ 720, **ou** fichier de moins de 150 Ko. Les recompresser ne ferait que perdre de la qualité pour
un gain négligeable. Les Motion Photos exportées en image fixe contournent délibérément cette
exemption et sont toujours compressées.

> La sortie est toujours du JPEG. Un PNG ou WebP transparent revient avec un fond noir.

---

## Résultat

```kotlin
data class PhotoChoiceResult(
    val uris: List<Uri>,    // URI sélectionnés, dans l'ordre de sélection
    val paths: List<String> // chemins locaux au mieux ; la chaîne de l'URI si non résolu
)
```

`paths` ne contient de vrais chemins de système de fichiers que pour les fichiers produits par la
bibliothèque elle-même (sortie compressée ou recadrée). Les éléments MediaStore renvoient leur URI
`content://` sous forme de chaîne.

| Média | Sans compression | Avec compression |
|-------|------------------|------------------|
| Image fixe | URI MediaStore `content://` | JPEG `file://` sous `cacheDir/photo_choice/compress_<uuid>.jpg` |
| Petite image (sous le seuil de contournement) | URI MediaStore `content://` | `content://` — inchangé |
| Vidéo | URI MediaStore `content://` | Inchangé |
| GIF | URI MediaStore `content://` | Inchangé (la compression perdrait l'animation) |
| Live Photo — garder le mouvement | URI MediaStore `content://` | Inchangé (mouvement préservé) |
| Live Photo — exporter en fixe | s. o. | JPEG compressé `file://` |
| Image recadrée | `file://` sous `cacheDir/photo_choice/crop_<timestamp>.jpg` | idem, puis compressée |

### Nettoyage

```kotlin
PhotoChoice.cleanup(context)
```

> **Cette méthode supprime tout, pas seulement les anciens fichiers.** `cleanup()` vide sans
> condition `cacheDir/photo_choice/`, `cacheDir/photo_choice_motion/` et
> `cacheDir/photo_choice_camera/`, et purge le cache mémoire des Motion Photos. Appelez-la **après**
> avoir consommé le résultat — un URI `file://` que vous détenez encore cesserait d'être résoluble.
>
> Le balayage à 24 heures est une routine interne distincte que la bibliothèque exécute d'elle-même ;
> vous n'avez pas à la planifier.

---

## Recettes

```kotlin
// Plusieurs images, jusqu'à 9
PhotoChoice.with(activity)
    .selectCount(9)
    .mediaType(MediaType.IMAGE)
    .spanCount(4)
    .showCamera(true)
    .forResult(activity) { result -> /* ... */ }

// Avatar : sélection simple + recadrage carré + compression
PhotoChoice.with(activity)
    .selectCount(1)
    .mediaType(MediaType.IMAGE)
    .cropConfig(CropConfig(enabled = true, aspectRatio = CropAspectRatio.SQUARE))
    .compressConfig(CompressConfig(enabled = true))
    .forResult(activity) { result -> /* ... */ }

// Vidéo uniquement, 60 s maximum
PhotoChoice.with(activity)
    .selectCount(1)
    .mediaType(MediaType.VIDEO)
    .showCamera(false)          // de toute façon masquée automatiquement en mode VIDEO
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }

// Images + vidéos — notez que le recadrage est indisponible en mode ALL
PhotoChoice.with(activity)
    .selectCount(9)
    .mediaType(MediaType.ALL)
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }
```

---

## Comportement en détail

### Sélection simple ou multiple

| Mode | Interface de la grille | Interaction |
|------|------------------------|-------------|
| Multiple (`selectCount > 1`) | Case à cocher + badge d'ordre | Toucher la case pour basculer ; toucher la vignette pour l'aperçu |
| Simple (`selectCount == 1`) | **Masque** la case, le badge d'ordre et le voile de désactivation | Toucher la vignette → aperçu, ou recadrage si activé |

La sélection simple n'a pas d'état intermédiaire « sélectionné » : c'est pourquoi les éléments
d'interface liés à la sélection disparaissent entièrement au lieu d'être désactivés.

### Prise de photo

Avec `showCamera(true)` (par défaut), la première cellule de la grille est une entrée caméra.

| Élément | Valeur |
|---------|--------|
| Répertoire | `DCIM/Camera` — le répertoire caméra public, c'est-à-dire l'album « Appareil photo » du système |
| Nom de fichier | `IMG` + les 8 derniers chiffres de l'horodatage + 4 chiffres aléatoires + `.jpg`, par ex. `IMG064001234821.jpg` |
| Format | JPEG |
| Zone temporaire | `cacheDir/photo_choice_camera/`, nettoyée par le nettoyeur de bac à sable |

Les photos sont insérées via le protocole en deux temps `IS_PENDING` de MediaStore : la ligne ne
devient visible pour la galerie système qu'une fois tous les octets écrits, si bien qu'aucune autre
application ne scanne un fichier partiel. En cas d'échec de la copie, la ligne en attente est
supprimée plutôt que laissée orpheline.

**Ce que l'application hôte doit faire : rien.** La bibliothèque déclare son propre `FileProvider`
avec l'autorité `${applicationId}.photochoice.fileprovider` — dérivée de l'`applicationId` de
l'hôte, elle ne peut donc jamais entrer en conflit avec un autre intégrateur. Aucune permission
caméra n'est requise non plus : la capture passe par `ACTION_IMAGE_CAPTURE`, et c'est l'application
appareil photo qui détient la permission.

> Si aucune application appareil photo n'est installée, toucher la tuile affiche un message au lieu
> de planter.
>
> Si votre application déclare `<uses-permission android:name="android.permission.CAMERA" />` dans
> son propre Manifest, Android exige alors que cette permission soit accordée avant de pouvoir
> utiliser l'intent. C'est une règle de la plateforme, pas une exigence de la bibliothèque.

Après une capture :

| Mode | Comportement |
|------|--------------|
| Multiple | La photo est sélectionnée automatiquement. Si `selectCount` est déjà atteint, un message de limite s'affiche et la photo reste dans la galerie |
| Simple + recadrage activé | Passe directement à l'écran de recadrage ; annuler rafraîchit la liste, la photo reste visible dans la grille |
| Simple + recadrage désactivé | Rafraîchit uniquement la liste et les données d'album — pas de sélection automatique |

L'album consulté par l'utilisateur ne change jamais ; seuls la liste et les agrégats d'albums sont
rafraîchis. Si cet album n'est pas « Appareil photo », la nouvelle photo apparaît après y être passé.

Quand `mediaType` vaut `VIDEO`, la tuile caméra est masquée automatiquement
(`effectiveShowCamera`) : une photo fixe ne pourrait jamais apparaître dans une liste vidéo, donc le
point d'entrée n'est pas proposé.

### Motion Photo / Live Photo

La bibliothèque traite les **Motion Photo, Google Motion Photo, photos animées Samsung** et fichiers
JPEG/HEIC similaires contenant une courte vidéo intégrée comme des motion photos. Ils restent de
type `IMAGE` de bout en bout.

**Dans la grille**

- Un badge **LIVE** s'affiche en bas à gauche de la vignette.
- **La pagination n'est jamais bloquée.** Un `load` de page ne lit que `IS_MOTION_PHOTO` de
  MediaStore de façon synchrone (API 34+) ; l'analyse XMP est asynchrone.
- **L'index est persistant.** Les résultats d'analyse survivent aux changements de configuration et
  à la mort du processus : rien n'est réanalysé à chaque ouverture.
- **La zone visible est prioritaire.** Un canal d'analyse haute priorité dédié couvre la zone
  visible et la fenêtre de préchargement, si bien qu'un défilement rapide n'est pas coincé derrière
  une file couvrant tout l'historique.
- Sur les appareils OEM qui n'exposent pas `IS_MOTION_PHOTO` — courant sur certains modèles — les
  badges dépendent de l'analyse XMP asynchrone en tête/queue de fichier, d'où un léger retard à la
  première apparition à l'écran, typiquement sous quelques centaines de millisecondes.

**Dans l'aperçu plein écran**

- Le badge LIVE se place sous la barre supérieure.
- **Appui long** pour lire la vidéo intégrée, **relâchement** pour l'arrêter. Le pincement et le
  zoom n'interrompent pas la lecture par accident.
- À l'entrée, le MP4 intégré est détecté et préchargé en arrière-plan, en cache sous
  `cacheDir/photo_choice_motion/`.

**Avec la compression activée**, l'aperçu propose un choix :

- **Garder le mouvement** (par défaut) — renvoie l'URI d'origine, sans compression.
- **Exporter en image fixe** — compression JPEG, mouvement abandonné.

---

## Architecture et performances

### Pagination

**Paging 3 sur un keyset MediaStore** (`DATE_ADDED` + `_ID`) — aucun parcours complet de curseur.

| Paramètre | Valeur |
|-----------|--------|
| Chargement initial | 500 éléments fixes, arrondis à la ligne supérieure |
| Taille de page | `spanCount × 25` éléments |
| Distance de préchargement | `spanCount × 35` éléments (~3 écrans) |
| Plafond mémoire | **Aucun.** `maxSize` n'est délibérément pas défini |

`maxSize` a été retiré volontairement : abandonner les pages les plus éloignées cassait le
remplissage des pages et faussait les totaux de l'aperçu. Le `load` d'une page n'effectue aucune
analyse XMP, ce qui garde le démarrage à froid et le défilement rapide fluides.

### Pipeline Motion Photo

```
Chargement de page MediaStore
    ├─ Synchrone : lot IS_MOTION_PHOTO en API 34+ → MediaFile.isMotionPhoto
    └─ Asynchrone (non bloquant) :
           ├─ Ouverture d'album : warmAlbumFromMediaStore
           ├─ Canal zone visible : visible + préchargement, analyse XMP haute priorité
           └─ Canal d'arrière-plan : fenêtre de préchargement basse priorité
```

Implémenté sous `data/motion/` : `MotionPhotoDetector`, `MotionPhotoListEnricher`,
`MotionPhotoXmpSniffer`, `MotionPhotoVideoResolver`.

### Répertoires du bac à sable

| Répertoire | Contenu | Rétention |
|------------|---------|-----------|
| `cacheDir/photo_choice/` | Sorties compressées et recadrées | Balayage 24 h ; vidé par `cleanup()` |
| `cacheDir/photo_choice_motion/` | Clips Motion Photo extraits | Balayage 24 h, plus des plafonds de 150 Mo / 50 fichiers |
| `cacheDir/photo_choice_camera/` | Fichiers temporaires de capture | Supprimés après chaque capture ; balayage 24 h en filet de sécurité |

### Dépendances clés

**Glide** pour les vignettes et les images d'aperçu · **Paging 3** pour la grille ·
**Media3 ExoPlayer** pour la lecture vidéo et Motion Photo · **ViewPager2** pour la pagination de
l'aperçu.

---

## Surface d'API publique

Seuls ces types constituent l'API supportée et résistante à l'obfuscation — ce sont ceux que
`consumer-rules.pro` conserve :

`PhotoChoice` · `PhotoChoice.Builder` · `PhotoChoiceContract` · `PhotoChoiceResult` ·
tout ce qui se trouve sous `config.**`

Les autres classes sont publiques au sens de la visibilité Kotlin et donc appelables
(`CameraHelper`, `CompressHelper`, `SandboxCleaner`, `DesignTokens` et consorts), mais ce sont des
**détails d'implémentation internes** : elles ne relèvent pas du versionnage sémantique et peuvent
changer ou disparaître dans n'importe quelle version. `PermissionHelper` fait seule exception : elle
est documentée ci-dessus et destinée à l'usage de l'hôte.

Ne lancez jamais directement `PhotoChoiceActivity`, `PreviewActivity` ou `CropActivity`.

### Sécurité de la configuration

Les entrées invalides sont assainies plutôt que rejetées par une exception : une mauvaise
configuration ne peut donc jamais faire planter la bibliothèque.

| Champ | Règle |
|-------|-------|
| `selectCount` | Conservé s'il est dans `1..9`, sinon **remis à `1`** |
| `spanCount` | Contraint dans `2..6` |
| `minVideoDurationMs` / `maxVideoDurationMs` | Échangés si min > max ; min plancher à `0` |
| `minImageSize` / `maxImageSize` | Échangés si min > max ; tous deux plancher à `0` |
| `cropConfig.enabled` | Exige la sélection simple **et** `MediaType.IMAGE` (`effectiveCropEnabled`) |
| `showCamera` | Forcé à off en mode `MediaType.VIDEO` (`effectiveShowCamera`) |

`PhotoChoiceConfig` expose ces bornes sous forme de constantes — `SELECT_COUNT_MIN` /
`SELECT_COUNT_MAX`, `SPAN_COUNT_MIN` / `SPAN_COUNT_MAX` — ainsi que les propriétés dérivées
`sanitized*` et `effective*`, si vous souhaitez refléter les valeurs effectives dans votre propre
interface.

---

## Structure du projet

```
photo_choice/
├── photo-choice/                    # la bibliothèque
│   └── src/main/java/com/google/photochoice/
│       ├── PhotoChoice.kt           # point d'entrée du Builder, forResult()
│       ├── PhotoChoiceContract.kt   # ActivityResultContract (recommandé)
│       ├── PhotoChoiceResult.kt
│       ├── config/                  # PhotoChoiceConfig, MediaType, ThemeMode, Crop/CompressConfig
│       ├── data/
│       │   ├── model/
│       │   └── motion/              # détection Motion Photo, analyse XMP, extraction de clip
│       ├── ui/
│       │   ├── grid/
│       │   ├── album/
│       │   ├── crop/                # CropActivity
│       │   ├── preview/             # PreviewActivity, lecture live à l'appui long
│       │   └── widget/
│       ├── util/                    # PermissionHelper, CameraHelper, CompressHelper, SandboxCleaner
│       └── viewmodel/
├── sample/                          # application de démonstration couvrant chaque option
├── docs/
│   ├── demo.mp4                     # vidéo de démonstration
│   ├── demo-poster.png              # affiche de la vidéo (clair / sombre)
│   ├── hero-light.png               # en-tête du README (clair / sombre)
│   ├── qr-sample-apk.png            # QR code de l'APK d'exemple
│   └── assets/make_assets.py        # régénère toutes les images ci-dessus
├── CHANGELOG.md
└── README.md                        # plus 7 traductions
```

### Build et vérification

```bash
./gradlew :photo-choice:assembleDebug
./gradlew :sample:installDebug
./gradlew test
./gradlew lint
```

Pour régénérer les visuels du README après un changement de palette ou de texte :

```bash
python docs/assets/make_assets.py
```

---

## Liste de contrôle d'intégration

- [ ] Dépendance ajoutée — JitPack ou `implementation(project(":photo-choice"))`
- [ ] Permissions de lecture des médias déclarées dans le Manifest de l'hôte
- [ ] Permission d'exécution demandée avant le lancement, via `PermissionHelper`
- [ ] API de lancement choisie — **`PhotoChoiceContract`** (résistant à la mort du processus) ou le callback `forResult`
- [ ] `null` (annulation) traité séparément d'un `PhotoChoiceResult`
- [ ] `PhotoChoice.cleanup(context)` appelé **après** consommation des sorties de recadrage/compression
- [ ] Pour Motion Photo + compression, choix **Garder le mouvement / Exporter en image fixe** compris

---

## Limitations

- La source de données se limite aux **médias publics de MediaStore** — pas les dossiers privés ou masqués.
- L'interface et les couleurs d'accent ne sont pas personnalisables ; seul `ThemeMode` clair / sombre / système l'est.
- Les filtres de durée vidéo n'affectent que l'affichage de la liste, jamais les fichiers sur disque.
- Le recadrage est indisponible en `MediaType.ALL` et en sélection multiple.
- Les badges LIVE sont quasi instantanés quand `IS_MOTION_PHOTO` est renseigné (API 34+), mais
  tardent un peu sur les appareils OEM sans ce champ en base. L'appui long dans l'aperçu détecte
  malgré tout les motion photos non signalées, via une détection complète incluant le XMP.

## Problèmes

Merci d'indiquer dans vos tickets la **version d'Android, le modèle de l'appareil, un extrait de
configuration, et le comportement attendu par rapport au comportement observé**. Pour les bugs
Motion Photo, précisez également si la galerie système reconnaît l'élément comme live.
