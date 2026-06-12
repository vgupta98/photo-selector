# Code map

<!--
  Read this first when you need to find where something lives, so you open the
  right two files instead of grepping the tree. Paths are from repo root; line
  numbers are deliberately omitted (they rot fastest). The behavioural rules,
  gotchas, and the grid invariants live in /CLAUDE.md, not here.
-->

Source root: `src/main/kotlin/com/vishalgupta/photoselector/`. Clean
architecture, single Gradle module: `domain` (pure) → `data` (impls) →
`presentation` (Compose), wired by hand in `di/AppContainer.kt`.

## Entry points

- `Main.kt` — process entry, window bootstrap.
- `App.kt` — root composable; owns the `Screen` back-stack and top-level state.
- `di/AppContainer.kt` — **manual DI; all wiring lives here.** Start here to
  trace a dependency or to add a screen/repository/decoder.

## domain/ — pure, no framework deps

- `model/` — entities: `Photo`, `PhotoId`, `RootFolder`, `Category`,
  `CategoryId`, `PhotoGroup` (`Single | Burst`; `Burst.keyIndex` = representative
  frame), `DecodedImage`, `ScanProgress`.
- `repository/` — interfaces: `PhotoRepository`, `CategoriesRepository`,
  `BrowsePositionRepository`, `PhotoExporter`, `PhotoTrash`.
- `usecase/` — `ScanRootFolderUseCase`, `CopyPhotosToFolderUseCase`,
  `ExportPhotosTxtUseCase`, `MovePhotosToTrashUseCase`.
- `grouping/` — the grouping seam: `PhotoGrouper` (an interface with one suspend
  `group(...)` method), `BurstGrouper` (object; time + camera), `SimilarityGrouper`
  (object; visual), `CaptureMetadata` + `CaptureMetadataSource`.
- `format/` — `PhotoDecoder`, `PhotoFormat`, `PhotoFormatRegistry` interfaces.

## data/ — implementations

- `filesystem/` — `FileSystemPhotoRepository` (scans a root into `Photo`s),
  `PathFilters` (include/exclude rules).
- `categories/` — `JsonCategoriesRepository` (membership persistence + v2
  migration), `CategoriesFile` (on-disk schema), `MembershipResolver`.
- `browse/` — `JsonBrowsePositionRepository` (persists last scroll position).
- `image/` — decode + cache: `SkikoImageLoader`, `ImageLoader`/`ImageCache`,
  `DiskThumbnailCache`.
- `format/` — per-format decoders (`JpegDecoder`, `PngDecoder`, `HeicDecoder`),
  `DefaultPhotoFormatRegistry`, `SkiaImageDecoding`; `ExifReader` (JPEG-only)
  backs `ExifCaptureMetadataSource`, memoised by `CachingCaptureMetadataSource`.
- `format/macos/` — `MacImageIO` (JNA→ImageIO bridge for HEIC; macOS only).
- `ai/` — similarity pipeline: `EmbeddingModel` seam → `OnnxEmbeddingModel`
  (default) / `DownscaleGrayEmbeddingModel` (fallback), `GrayBuffer`,
  `SharpnessScorer`, `PhotoFeatureExtractor` (+ `PhotoFeatures`), `EmbeddingCache`,
  `SimilarityPhotoGrouper` (adapter onto `PhotoGrouper`).
- `export/` — `CopyPhotoExporter`, `TxtPhotoExporter`, `CompositePhotoExporter`.
- `trash/` — `DesktopPhotoTrash` (move-to-Trash via AWT Desktop).
- `io/` — `AtomicJsonWriter` (shared atomic JSON write; categories + browse).

## presentation/ — Compose + view models, by screen

- `navigation/` — `Screen` (sealed: `RootPicker | Grid | Browser | Compare |
  Survey`), `CategoryScope`.
- `StateHolder.kt` — base view-model plumbing.
- `rootpicker/` — `RootFolderPickerScreen` + `…ViewModel`.
- `grid/` — **the heart of the app.** `GridViewModel` (focus/select/file; holds
  the `expandedBurstId` state; `refocus` re-anchors by identity), `GridScreen`
  (render; defines `tileIndexForFlat`, the tile↔flat translation),
  `GridDisplayModel` (top-level tile-index *helpers*, not a class —
  `displayGroupsFor`/`buildRenderItems` explode the open burst into per-frame
  tiles, plus `renderIndexForTile` etc.), `GridViewportAnchor` (scroll anchoring).
- `browser/` — `BrowserScreen` + `…ViewModel`, `ZoomableImage`, `ZoomState`.
- `compare/` — `CompareScreen` + `…ViewModel` (two-up, shared zoom).
- `survey/` — `SurveyScreen` + `…ViewModel` (3+ overview pick).
- `common/` — non-UI plumbing: `NativeFileDialogs`, `MacSystemActions` /
  `SystemActions`, `CategoryHotkeys`, `CategoryToggle`, `GroupingMode`,
  `HoverOverlay`, `PlatformLabels`.

## presentation/designsystem/ — Atomic Design

- `theme/` — tokens: `AppColors`/`Spacing`/`Dimens` read via `AppTheme.*` (those
  three only); `AppTypography`/`AppShapes` go via `MaterialTheme`. Files:
  `Color`, `Spacing`, `Dimens`, `Type`, `Shape`.
- `atom/` — `Buttons`, `FavouriteStar`, `LoadingIndicator`.
- `molecule/` — incl. `GroupingModeToggle`, `BurstExpandedHeader`/`Footer`, the
  `*KeyboardLegend` set, `CategoryMenu`/`CategoryActionsMenu`, `PillToast`,
  `SelectionFileMenu`, `ConfirmDialog`/`CategoryNameDialog`.
- `organism/` — `GridTopBar`/`GridSelectionTopBar`, `BrowserTopBar`/
  `BrowserCategoryHud`, `PhotoThumbnail`, `ComparePaneView`, `SurveyTileView`,
  `TopBarScaffold`.

## By task — open these first

| Task | Files |
| --- | --- |
| Grid focus / selection / keyboard filing | `grid/GridViewModel.kt`, `grid/GridDisplayModel.kt` |
| Burst expand-in-place behaviour | `grid/GridViewModel.kt` (`expandedBurstId` state), `grid/GridDisplayModel.kt` (`displayGroupsFor`/`buildRenderItems`), molecule `BurstExpanded*` |
| Grouping lens (Off / Time / Similarity) | `domain/grouping/`, `data/ai/SimilarityPhotoGrouper.kt`, `grid/GridViewModel.kt` |
| Scroll position / index translation | `grid/GridScreen.kt` (`tileIndexForFlat`), `grid/GridViewportAnchor.kt`, `grid/GridDisplayModel.kt`, `data/browse/` |
| Categories / Favourites | `data/categories/`, `domain/repository/CategoriesRepository.kt`, `common/CategoryHotkeys.kt` |
| Decoding a new format | `domain/format/PhotoDecoder.kt`, `data/format/DefaultPhotoFormatRegistry.kt`, register in `di/AppContainer.kt` |
| HEIC specifics | `data/format/HeicDecoder.kt`, `data/format/macos/MacImageIO.kt` |
| Similarity embeddings / model swap | `data/ai/OnnxEmbeddingModel.kt`, `data/ai/EmbeddingCache.kt`, `tools/embedding-model/` |
| Compare / Survey views | `presentation/compare/`, `presentation/survey/` |
| Adding a screen | `presentation/navigation/Screen.kt`, `App.kt`, `di/AppContainer.kt` |
| Theming / new shared component | `presentation/designsystem/` (theme → atom → molecule → organism) |
| Export / trash | `data/export/`, `data/trash/`, matching `domain/usecase/` |

## Key build files

- `build.gradle.kts` — version (single source of truth), Compose Desktop config,
  DMG packaging.
- `.github/workflows/` — release machinery (see [`release.md`](release.md)).
- `tools/embedding-model/` — reproducible export of the bundled similarity ONNX
  model (pinned `requirements.txt`, `export_mobilenetv3.py`, expected SHA-256 in
  its `README.md`).
- `scripts/dry-run-release.sh` — local dry-run of the release logic.
