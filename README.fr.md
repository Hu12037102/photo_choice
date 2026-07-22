# PhotoChoice

[English Documentation](README.md) | [简体中文文档](README.zh-CN.md) | [日本語ドキュメント](README.ja.md) | [한국어 문서](README.ko.md) | [Documentación en español](README.es.md) | [الوثائق العربية](README.ar.md)

Bibliothèque de sélecteur de photos Android : grille multi-sélection, changement d'album, aperçu plein écran, tuile caméra optionnelle, recadrage d'image unique, compression optionnelle, et détection **Motion Photo / Live Photo** avec lecture dans l'aperçu. Intégrez via une **API Builder** — ne lancez pas les Activity internes directement.

- **Package** : `com.google.photochoice`
- **Min SDK** : 29 (Android 10, Scoped Storage ; lecture des médias publics sans permission d'écriture héritée)
- **Target SDK** : 36
- **Langage** : Kotlin
- **Licence** : [Apache License 2.0](LICENSE)

---

## Fonctionnalités

| Fonctionnalité | Description |
|----------------|-------------|
| Types de médias | Images uniquement / vidéos uniquement / images + vidéos |
| Sélection | Simple ou multiple (`selectCount` 1–9) |
| Albums | Agrégation des buckets MediaStore avec sélecteur déroulant |
| Grille | Colonnes configurables (2–6), vignettes carrées, Paging 3 |
| En-tête de date au défilement | Affiche la date de la zone visible pendant le défilement |
| Caméra | Tuile caméra optionnelle en première cellule (enregistre dans la galerie système) |
| Aperçu | Balayage plein écran ; lecture vidéo intégrée (appui pour lire, appui pendant la lecture bascule uniquement l'interface) |
| Motion Photo | Badge LIVE sur la grille ; appui long pour lire le clip intégré dans l'aperçu |
| Recadrage | Sélection simple + mode image ; `CropActivity` autonome |
| Compression | Redimensionnement JPEG + qualité optionnels à la fin ; les Live Photos peuvent conserver le mouvement ou exporter en statique |
| Thème | Clair / sombre / suivre le système (par Activity, ne remplace jamais l'app hôte globalement) |
| API de lancement | Double voie : **`PhotoChoiceContract`** (recommandé, sans état statique) ou callback **`forResult`** |
| Résilience au process death | Le mode Contract survit à la recréation d'Activity et au process death ; le mode callback a une détection de dégradation gracieuse |

### Sélection simple vs multiple

| Mode | UI grille | Interaction |
|------|-----------|-------------|
| Multiple (`selectCount > 1`) | Case à cocher + badge d'ordre de sélection | Appui sur la case pour basculer ; appui sur la vignette pour l'aperçu |
| Simple (`selectCount = 1`) | **Masque** case à cocher, badge d'ordre, overlay désactivé | Appui sur la vignette → aperçu ou recadrage (si activé) |

---

## Motion Photo / Live Photo

La bibliothèque traite les **Motion Photo, Google Motion Photo, photos animées Samsung**, et fichiers JPEG/HEIC similaires avec courte vidéo intégrée comme motion photos (toujours de type `IMAGE`).

### Liste grille

- Badge **LIVE** en bas à gauche des vignettes.
- **Ne bloque pas le paging** : le `load` de page ne lit que `IS_MOTION_PHOTO` de MediaStore (API 34+) de façon synchrone ; le sniff XMP rapide s'exécute de façon asynchrone.
- **Index persistant** : les résultats scannés survivent aux changements de configuration et au process death ; pas de re-sniff à chaque ouverture.
- **Priorité viewport** : canal de sniff haute priorité dédié à la fenêtre visible + prefetch uniquement — le défilement rapide n'est pas bloqué par une file d'attente historique complète.
- Sur les OEM qui omettent `IS_MOTION_PHOTO` (courant sur certains appareils), les badges reposent sur le sniff XMP tête/queue asynchrone ; l'apparition initiale à l'écran peut être légèrement retardée (généralement moins de quelques centaines de ms).

### Aperçu plein écran

- Badge LIVE sous la barre supérieure.
- **Appui long** pour lire la vidéo intégrée, **relâcher** pour arrêter ; le pincement/zoom n'arrête pas la lecture par accident.
- Détection en arrière-plan + préchargement du MP4 intégré à l'entrée (mis en cache sous `cacheDir/photo_choice_motion/`).

### Compression et export

Lorsque `CompressConfig` est activé, l'aperçu propose **Conserver le live / Exporter en statique** :

- **Conserver le live** (par défaut) : retourne l'URI d'origine, pas de compression.
- **Exporter en statique** : compression JPEG, mouvement abandonné.

---

## Démarrage rapide

### 1. Ajouter le module

Dans le `settings.gradle.kts` de l'hôte :

```kotlin
include(":photo-choice")
```

Dans le `build.gradle.kts` de l'app ou du module fonctionnel :

```kotlin
dependencies {
    implementation(project(":photo-choice"))
}
```

> Actuellement intégré comme **module source**. Remplacez par vos coordonnées Maven une fois publié.

### 2. Permissions

La bibliothèque déclare les permissions de lecture média dans son Manifest ; **l'app hôte doit déclarer les mêmes permissions** et les demander à l'exécution.

| Version Android | Permissions |
|-----------------|-------------|
| API 34+ | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` (selon `mediaType`), `READ_MEDIA_VISUAL_USER_SELECTED` déclaré ; autorisation partielle traitée comme utilisable |
| API 33 | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` (selon `mediaType`) |
| API 29–32 | `READ_EXTERNAL_STORAGE` |

Utilisez `PermissionHelper` pour la liste des permissions et la vérification :

```kotlin
import com.google.photochoice.util.PermissionHelper

if (PermissionHelper.hasMediaPermission(context)) {
    openPhotoChoice()
} else {
    requestPermissionLauncher.launch(PermissionHelper.requiredMediaPermissions())
}
```

Voir **`sample`** / `MainActivity` pour un exemple complet.

### 3. Lancer le sélecteur (recommandé : Contract)

Utilisez `ActivityResultContract` pour une intégration **résistante au process death** :

```kotlin
import com.google.photochoice.PhotoChoiceContract
import com.google.photochoice.PhotoChoice
import com.google.photochoice.config.MediaType

val launcher = registerForActivityResult(PhotoChoiceContract()) { result ->
    if (result == null) {
        // Utilisateur a annulé
        return@registerForActivityResult
    }
    result.uris.forEach { uri ->
        // URI content:// ou file://
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

**Le mode Contract** passe la config via les extras Intent, le résultat via `setResult()` — tous deux gérés par le système, survivant à la recréation d'Activity et au process death. Aucune variable statique. **Préféré pour toute utilisation en production.**

### 4. Alternative : API callback (legacy)

Depuis une **`FragmentActivity`** (ou `AppCompatActivity`) :

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
            // Utilisateur a annulé
            return@forResult
        }
        result.uris.forEach { uri ->
            // URI content:// ou file://
        }
    }
```

**Important :** L'API callback utilise des champs statiques en interne et **ne survit pas** à la recréation d'Activity hôte ou au process death. Si l'Activity du sélecteur tourne pendant que l'hôte est tué, le callback est perdu et le sélecteur se ferme proprement sans résultat. Pour la fiabilité, utilisez l'approche Contract ci-dessus.

---

## Résultat

```kotlin
data class PhotoChoiceResult(
    val uris: List<Uri>,    // URIs sélectionnés dans l'ordre de sélection
    val paths: List<String> // Chemins locaux best-effort ; chaîne URI si non résolu
)
```

| Type de média | Sans compression | Avec compression |
|---------------|------------------|------------------|
| Image statique | URI MediaStore `content://` | JPEG compressé `file://` sous `cacheDir/photo_choice/compress_*.jpg` |
| Vidéo | URI MediaStore `content://` | Inchangé (les vidéos ne sont jamais compressées) |
| GIF | URI MediaStore `content://` | Inchangé (la compression perdrait l'animation) |
| Live Photo (conserver le live) | URI MediaStore `content://` | Inchangé (mouvement préservé) |
| Live Photo (export statique) | N/A | JPEG compressé `file://` sous `cacheDir/photo_choice/compress_*.jpg` |

Nettoyer les fichiers cache obsolètes :

```kotlin
PhotoChoice.cleanup(context)
```

Supprime les fichiers sandbox de plus de 24 heures (appelez après traitement du résultat si nécessaire).

---

## API Builder

| Méthode | Type | Défaut | Description |
|---------|------|--------|-------------|
| `selectCount` | `Int` | `9` | `1` = simple, `>1` = multiple ; auto-clampé à `1..9` |
| `mediaType` | `MediaType` | `IMAGE` | `IMAGE` / `VIDEO` / `ALL` |
| `spanCount` | `Int` | `3` | Colonnes grille ; auto-clampé à **2–6** |
| `showCamera` | `Boolean` | `true` | Afficher la tuile caméra en première cellule |
| `minImageSize` | `Long` | `0` | Taille minimale fichier image (octets) ; filtre les petites icônes. Images uniquement |
| `maxImageSize` | `Long` | `Long.MAX_VALUE` | Taille maximale fichier image (octets) ; filtre les images surdimensionnées. Images uniquement |
| `minVideoDuration` | `Long` | `0` | Durée minimale vidéo (ms) ; auto-échangé si > maxVideoDuration |
| `maxVideoDuration` | `Long` | `60000` | Durée maximale vidéo (ms) ; auto-échangé si < minVideoDuration |
| `themeMode` | `ThemeMode` | `FOLLOW_SYSTEM` | `LIGHT` / `DARK` / `FOLLOW_SYSTEM` (par Activity, jamais global) |
| `cropConfig` | `CropConfig` | voir ci-dessous | Paramètres de recadrage |
| `compressConfig` | `CompressConfig` | voir ci-dessous | Compression à la fin |

Construire séparément pour l'usage Contract :

```kotlin
val config = PhotoChoice.with(context)
    .selectCount(1)
    .buildConfig()  // retourne PhotoChoiceConfig directement
```

### Recadrage `CropConfig`

Uniquement lorsque **`selectCount = 1`** et **`mediaType` inclut des images** — ouvre `CropActivity` autonome.
Le recadrage est automatiquement désactivé (dégradation silencieuse) pour le mode vidéo seule ou multi-sélection.

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

Avec sélection simple + recadrage activé, choisir une image va directement au recadrage, puis retourne et ferme le sélecteur.

### Compression `CompressConfig`

À **Terminé**, met à l'échelle et compresse en JPEG les **images** avant le callback ; vidéos, GIF et Live Photos (mode conserver le live) ne sont pas compressés. Les Motion Photos conservent le live par défaut ; basculez en statique dans l'aperçu avant compression.

**Stratégie par défaut (alignée sur les réglages courants type WeChat Moments) :**

| Paramètre | Défaut | Description |
|-----------|--------|-------------|
| `maxWidth` / `maxHeight` | `1280` | Limite du côté le plus long |
| `quality` | `80` | Qualité JPEG initiale |
| `maxFileSizeBytes` | `1572864` (~1,5 Mo) | Si dépassé, qualité réduite par étapes ; `0` = pas de limite de taille |
| `minQuality` | `50` | Qualité minimale lors de l'itération de taille |
| `qualityStep` | `10` | Pas de réduction de qualité à chaque étape |

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

> **Note :** La sortie est toujours en JPEG. Les PNG/WebP transparents auront un fond noir après compression (comportement similaire à WeChat et autres apps majeures).

---

## Recettes

### Images multiples (jusqu'à 9)

```kotlin
PhotoChoice.with(activity)
    .selectCount(9)
    .mediaType(MediaType.IMAGE)
    .spanCount(4)
    .showCamera(true)
    .forResult(activity) { result -> /* ... */ }
```

### Avatar (simple + recadrage carré)

```kotlin
PhotoChoice.with(activity)
    .selectCount(1)
    .mediaType(MediaType.IMAGE)
    .cropConfig(CropConfig(enabled = true, aspectRatio = CropAspectRatio.SQUARE))
    .compressConfig(CompressConfig(enabled = true))
    .forResult(activity) { result -> /* ... */ }
```

### Vidéo uniquement (max 60 s)

```kotlin
PhotoChoice.with(activity)
    .selectCount(1)
    .mediaType(MediaType.VIDEO)
    .showCamera(false)
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }
```

### Images + vidéos

```kotlin
PhotoChoice.with(activity)
    .selectCount(9)
    .mediaType(MediaType.ALL)
    .maxVideoDuration(60_000L)
    .forResult(activity) { result -> /* ... */ }
```

---

## Application exemple

Le module **`sample`** démontre toutes les options :

```bash
./gradlew :sample:installDebug
```

Exécutez **PhotoChoice Sample**, ajustez les paramètres, ouvrez le sélecteur et prévisualisez les médias sélectionnés depuis la liste de résultats.

---

## Architecture et performances

### Paging

**Paging 3 + keyset MediaStore** (`DATE_ADDED` + `_ID`) — pas de scan Cursor complet :

| Paramètre | Exemple (`spanCount = 3`) |
|-----------|---------------------------|
| Chargement initial | ~15 lignes × colonnes ≈ 45 éléments |
| Taille de page | ~25 lignes × colonnes ≈ 75 éléments |
| Distance de prefetch | ~35 lignes × colonnes ≈ 105 éléments (~3 écrans) |
| Plafond mémoire | ~900–1200 éléments de métadonnées (supprime les pages les plus éloignées) |

Le `load` de page **n'exécute pas l'analyse XMP** — démarrage à froid et défilement rapide restent fluides.

### Pipeline Motion Photo

```
Chargement page MediaStore
    ├─ Sync : API 34+ batch IS_MOTION_PHOTO → MediaFile.isMotionPhoto
    └─ Async (non bloquant) :
           ├─ Ouverture album : warmAlbumFromMediaStore
           ├─ Canal viewport : visible + prefetch, sniff XMP haute priorité
           └─ Canal arrière-plan : fenêtre prefetch basse priorité
```

Modules sous `data/motion/` : `MotionPhotoDetector`, `MotionPhotoListEnricher`, `MotionPhotoXmpSniffer`, `MotionPhotoVideoResolver`.

### Dépendances clés

- **Glide** — vignettes et images d'aperçu
- **Paging 3** — paging grille
- **Media3 ExoPlayer** — lecture vidéo aperçu / Motion Photo
- **ViewPager2** — paging aperçu

---

## Sécurité de configuration

PhotoChoice applique une **sanitisation défensive** à toutes les valeurs de configuration exposées, afin qu'une entrée invalide ne fasse jamais crasher la bibliothèque :

| Champ | Sanitisation |
|-------|--------------|
| `selectCount` | clampé à `1..9` ; hors plage retourne `1` |
| `spanCount` | clampé à `2..6` |
| `minVideoDurationMs` / `maxVideoDurationMs` | auto-échangé si min > max ; min clampé à `>= 0` |
| `minImageSize` / `maxImageSize` | auto-échangé si min > max ; min clampé à `>= 0` |
| `cropConfig.enabled` | auto-désactivé pour le mode VIDEO ou multi-sélection (`effectiveCropEnabled`) |

---

## Structure du projet

```
photo_choice/
├── photo-choice/              # Bibliothèque (API publique : PhotoChoice)
│   └── src/main/java/com/google/photochoice/
│       ├── PhotoChoice.kt     # Entrée Builder, forResult()
│       ├── PhotoChoiceContract.kt     # ActivityResultContract (recommandé)
│       ├── config/
│       ├── data/
│       │   └── motion/        # Détection Motion Photo, XMP, extraction vidéo
│       ├── viewmodel/
│       └── ui/
│           ├── grid/
│           ├── album/
│           ├── crop/          # CropActivity
│           └── preview/       # PreviewActivity, lecture live par appui long
├── sample/
├── PRD.md                     # Spécification produit interne
├── README.md                  # English documentation
├── README.zh-CN.md            # 简体中文文档
├── README.ja.md               # 日本語ドキュメント
├── README.ko.md               # 한국어 문서
├── README.fr.md               # Ce document (français)
├── README.es.md               # Documentación en español
└── README.ar.md               # الوثائق العربية
```

---

## Build et vérification

```bash
./gradlew :photo-choice:assembleDebug
./gradlew :sample:installDebug
./gradlew test
./gradlew lint
```

---

## Liste de contrôle d'intégration

- [ ] `implementation(project(":photo-choice"))` (ou équivalent Maven)
- [ ] Permissions de lecture média dans le Manifest hôte
- [ ] Permission runtime avant lancement (`PermissionHelper`)
- [ ] Choisir l'API de lancement : **`PhotoChoiceContract`** (recommandé, résistant au process death) ou callback `forResult`
- [ ] Gérer `null` (annulation) vs `PhotoChoiceResult` (succès)
- [ ] Appeler `PhotoChoice.cleanup(context)` si compression/recadrage utilisés
- [ ] Pour Live Photos + compression, comprendre **Conserver le live / Exporter en statique** dans l'aperçu

---

## Limitations

- La source de données est **uniquement les médias publics MediaStore** — pas les dossiers privés/cachés.
- Les couleurs UI et d'accent ne sont pas personnalisables ; seul `ThemeMode` clair/sombre/système.
- Ne **lancez pas** `PhotoChoiceActivity`, `PreviewActivity` ou `CropActivity` directement.
- Les filtres de durée vidéo affectent uniquement la liste, pas les fichiers sur disque.
- **Badges LIVE** :
  - Quasi instantané quand API 34+ et `IS_MOTION_PHOTO` est défini dans MediaStore.
  - Bref délai sur les OEM sans drapeaux DB (sniff XMP asynchrone à la première entrée viewport).
  - L'appui long en aperçu détecte toujours les motion photos non marquées via détection complète (incl. XMP).

---

## Problèmes

Veuillez inclure **version Android, modèle d'appareil, extrait de config, comportement attendu vs réel**. Pour les bugs Motion Photo, indiquez si la galerie système reconnaît l'élément comme live/Motion Photo.
