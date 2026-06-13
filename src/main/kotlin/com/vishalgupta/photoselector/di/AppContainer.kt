package com.vishalgupta.photoselector.di

import com.vishalgupta.photoselector.data.browse.JsonBrowsePositionRepository
import com.vishalgupta.photoselector.data.export.CompositePhotoExporter
import com.vishalgupta.photoselector.data.export.CopyPhotoExporter
import com.vishalgupta.photoselector.data.export.TxtPhotoExporter
import com.vishalgupta.photoselector.data.categories.JsonCategoriesRepository
import com.vishalgupta.photoselector.data.filesystem.FileSystemPhotoRepository
import com.vishalgupta.photoselector.data.ai.DownscaleGrayEmbeddingModel
import com.vishalgupta.photoselector.data.ai.EmbeddingCache
import com.vishalgupta.photoselector.data.ai.EmbeddingModel
import com.vishalgupta.photoselector.data.ai.OnnxEmbeddingModel
import com.vishalgupta.photoselector.data.ai.PhotoFeatureExtractor
import com.vishalgupta.photoselector.data.ai.SimilarityPhotoGrouper
import com.vishalgupta.photoselector.data.trash.DesktopPhotoTrash
import com.vishalgupta.photoselector.data.format.CachingCaptureMetadataSource
import com.vishalgupta.photoselector.data.format.DefaultPhotoFormatRegistry
import com.vishalgupta.photoselector.data.format.ExifCaptureMetadataSource
import com.vishalgupta.photoselector.data.format.HeicDecoder
import com.vishalgupta.photoselector.data.format.JpegDecoder
import com.vishalgupta.photoselector.data.format.PngDecoder
import com.vishalgupta.photoselector.data.format.RawDecoder
import com.vishalgupta.photoselector.data.image.DiskThumbnailCache
import com.vishalgupta.photoselector.data.image.ImageLoader
import com.vishalgupta.photoselector.data.image.SkikoImageLoader
import com.vishalgupta.photoselector.domain.format.PhotoFormatRegistry
import com.vishalgupta.photoselector.domain.grouping.PhotoGrouper
import com.vishalgupta.photoselector.domain.model.DecodedImage
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.repository.BrowsePosition
import com.vishalgupta.photoselector.domain.repository.CategoriesRepository
import com.vishalgupta.photoselector.domain.repository.BrowsePositionRepository
import com.vishalgupta.photoselector.domain.repository.PhotoExporter
import com.vishalgupta.photoselector.domain.repository.PhotoRepository
import com.vishalgupta.photoselector.domain.repository.PhotoTrash
import com.vishalgupta.photoselector.domain.usecase.CopyPhotosToFolderUseCase
import com.vishalgupta.photoselector.domain.usecase.ExportPhotosTxtUseCase
import com.vishalgupta.photoselector.domain.usecase.MovePhotosToTrashUseCase
import com.vishalgupta.photoselector.domain.usecase.ScanRootFolderUseCase
import com.vishalgupta.photoselector.presentation.browser.BrowserViewModel
import com.vishalgupta.photoselector.presentation.compare.CompareViewModel
import com.vishalgupta.photoselector.presentation.grid.GridViewModel
import com.vishalgupta.photoselector.presentation.survey.SurveyViewModel
import com.vishalgupta.photoselector.presentation.navigation.CategoryScope
import com.vishalgupta.photoselector.presentation.navigation.GridRetentionKey
import com.vishalgupta.photoselector.presentation.navigation.activeCategoryId
import com.vishalgupta.photoselector.presentation.navigation.slice
import com.vishalgupta.photoselector.presentation.common.GroupingMode
import com.vishalgupta.photoselector.presentation.common.MacSystemActions
import com.vishalgupta.photoselector.presentation.common.SystemActions
import com.vishalgupta.photoselector.presentation.navigation.Screen
import com.vishalgupta.photoselector.presentation.rootpicker.RootFolderPickerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import java.nio.file.Path

class AppContainer {

    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val imageDecodeDispatcher = Dispatchers.IO.limitedParallelism(
        Runtime.getRuntime().availableProcessors().coerceAtMost(4).coerceAtLeast(2),
    )

    private val appScope = CoroutineScope(SupervisorJob() + imageDecodeDispatcher)
    private var _folderJob = SupervisorJob(appScope.coroutineContext[Job])
    val folderJob: Job get() = _folderJob

    private val formatRegistry: PhotoFormatRegistry = DefaultPhotoFormatRegistry(
        // HEIC and RAW are macOS-only for now (both ride the ImageIO bridge); off macOS they're
        // simply unregistered, so those files fall through the scanner rather than registering a
        // throwing decoder.
        decoders = buildList {
            add(JpegDecoder())
            add(PngDecoder())
            if (HeicDecoder.isSupportedOnThisPlatform()) add(HeicDecoder())
            if (RawDecoder.isSupportedOnThisPlatform()) add(RawDecoder())
        },
    )

    private val cacheDir: Path =
        Path.of(System.getProperty("user.home"), "Library", "Caches", "PhotoSelector")

    private val diskThumbnailCache = DiskThumbnailCache(cacheDir = cacheDir)
        .also { it.startEviction(appScope) }

    val imageLoader: ImageLoader = SkikoImageLoader(
        registry = formatRegistry,
        decodeDispatcher = imageDecodeDispatcher,
        diskCache = diskThumbnailCache,
    )

    // Shared across grids so each photo's EXIF (capture time + camera) is read at most once per
    // session; the cache is what makes burst re-grouping on every re-slice cheap.
    private val captureMetadataSource = CachingCaptureMetadataSource(ExifCaptureMetadataSource())

    // Visual-similarity grouping (GroupingMode.Similarity). The learned MobileNetV3-Small embedder
    // (OnnxEmbeddingModel) is the on-device default; if its bundled blob can't load we fall back to
    // the classical, dependency-free DownscaleGrayEmbeddingModel so the lens still works. Either way
    // embeddings are derived from a small decode and persisted, keyed by content + model id, so the
    // cost is paid once and survives a restart (and a model swap re-keys the cache automatically).
    private val embeddingModel: EmbeddingModel = loadEmbeddingModel()
    private val embeddingCache = EmbeddingCache(cacheDir = cacheDir, modelId = embeddingModel.id)
        .also { it.startEviction(appScope) }
    private val similarityGrouper: PhotoGrouper = SimilarityPhotoGrouper(
        PhotoFeatureExtractor(
            model = embeddingModel,
            cache = embeddingCache,
            decode = ::decodeForEmbedding,
        ),
    )

    private fun loadEmbeddingModel(): EmbeddingModel = try {
        OnnxEmbeddingModel.Loader.fromResource()
    } catch (t: Throwable) {
        System.err.println("ONNX embedder unavailable, falling back to classical embedder: ${t.message}")
        DownscaleGrayEmbeddingModel()
    }

    private suspend fun decodeForEmbedding(photo: Photo): DecodedImage? = try {
        formatRegistry.decoderFor(photo.absolutePath)?.decode(photo.absolutePath, EMBEDDING_EDGE_PX)
    } catch (_: Throwable) {
        null
    }

    private val photoRepository: PhotoRepository = FileSystemPhotoRepository(formatRegistry)
    private val categoriesRepository: CategoriesRepository =
        JsonCategoriesRepository(json, scannedPhotos = { root -> photosFor(root) })
    private val browsePositionRepository: BrowsePositionRepository = JsonBrowsePositionRepository(json)
    private val exporter: PhotoExporter = CompositePhotoExporter(TxtPhotoExporter(), CopyPhotoExporter())
    private val photoTrash: PhotoTrash = DesktopPhotoTrash()

    private val scanUseCase = ScanRootFolderUseCase(photoRepository)
    private val exportTxtUseCase = ExportPhotosTxtUseCase(exporter)
    private val copyPhotosUseCase = CopyPhotosToFolderUseCase(exporter)
    private val movePhotosToTrashUseCase = MovePhotosToTrashUseCase(photoTrash)

    val systemActions: SystemActions = MacSystemActions()

    val currentScreen = MutableStateFlow<Screen>(Screen.RootPicker)
    val currentPhotoPath = MutableStateFlow<Path?>(null)

    private var scannedPhotos: List<Photo> = emptyList()
    private var scannedRoot: RootFolder? = null

    // Live grids kept alive per (root, scope) for the session. A Grid -> Browser -> Grid round trip
    // reuses the retained view model (and the App keeps its scroll position) so the grid returns
    // exactly as it was left, instead of rebuilding and re-anchoring. Cleared on a root change.
    private val retainedGrids = mutableMapOf<GridRetentionKey, GridViewModel>()

    // The grouping lens the user last picked, remembered for the session so the first grid built for
    // each (root, scope) opens in that lens rather than the default. Retained grids keep their own
    // lens across navigation; this seed only covers a freshly built one (a first visit, or a new scope).
    private var lastGroupingMode: GroupingMode = GroupingMode.Time

    private fun photosFor(root: RootFolder): List<Photo> =
        if (scannedRoot?.path == root.path) scannedPhotos else emptyList()

    fun loadBrowsePosition(root: RootFolder): BrowsePosition = browsePositionRepository.load(root)

    private fun setScanResult(root: RootFolder, photos: List<Photo>) {
        scannedRoot = root
        scannedPhotos = photos
    }

    /**
     * Drops just-trashed photos from the scan snapshot so any screen built *after* the delete
     * (navigation rebuilds every view model from [photosFor]) is constructed without them. The
     * live view model that triggered the delete updates its own in-memory copy separately.
     */
    private fun removeScannedPhotos(ids: Set<PhotoId>) {
        if (ids.isEmpty()) return
        scannedPhotos = scannedPhotos.filterNot { it.id in ids }
        // Keep every retained grid's own photo list in step: a delete from the browser must not
        // reappear when the user returns to a grid that is now reused rather than rebuilt. The grid
        // that originated the delete already pruned itself, so its own notification is a no-op.
        retainedGrids.values.forEach { it.removePhotos(ids) }
    }

    fun goTo(screen: Screen) {
        if (screen !is Screen.Browser) currentPhotoPath.value = null
        currentScreen.value = screen
    }

    fun rootPickerViewModel(): RootFolderPickerViewModel = RootFolderPickerViewModel(
        scanRootFolder = scanUseCase,
        onScanComplete = { root, photos ->
            imageLoader.evictAll()
            setScanResult(root, photos)
            val position = browsePositionRepository.load(root)
            val scrollIndex = position.lastIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
            goTo(Screen.Grid(root, initialScrollIndex = scrollIndex, lastViewedPhotoId = position.lastPhotoId))
        },
        parentJob = appScope.coroutineContext[Job],
    )

    fun browserViewModel(
        root: RootFolder,
        initialIndex: Int,
        scope: CategoryScope,
    ): BrowserViewModel = BrowserViewModel(
        root = root,
        photos = photosForScope(root, scope),
        initialIndex = initialIndex,
        categories = categoriesRepository,
        moveToTrash = movePhotosToTrashUseCase,
        imageLoader = imageLoader,
        isReadOnly = categoriesRepository.isReadOnly(root),
        parentJob = folderJob,
        onPhotosDeleted = { ids -> removeScannedPhotos(ids) },
        // Only All Photos owns the per-root scroll index; a category view (its own
        // pushed instance) just records the last photo so a re-open lands near it.
        onPositionChanged = when (scope) {
            CategoryScope.AllPhotos -> { position ->
                appScope.launch { browsePositionRepository.save(root, position) }
            }
            is CategoryScope.Category -> { position ->
                appScope.launch { browsePositionRepository.saveLastPhotoId(root, position.lastPhotoId) }
            }
        },
    )

    fun compareViewModel(
        root: RootFolder,
        scope: CategoryScope,
        leftIndex: Int,
        rightIndex: Int,
    ): CompareViewModel = CompareViewModel(
        root = root,
        photos = photosForScope(root, scope),
        leftIndex = leftIndex,
        rightIndex = rightIndex,
        categories = categoriesRepository,
        imageLoader = imageLoader,
        isReadOnly = categoriesRepository.isReadOnly(root),
        parentJob = folderJob,
    )

    fun surveyViewModel(
        root: RootFolder,
        scope: CategoryScope,
        indices: List<Int>,
    ): SurveyViewModel = SurveyViewModel(
        root = root,
        photos = photosForScope(root, scope),
        indices = indices,
        categories = categoriesRepository,
        imageLoader = imageLoader,
        isReadOnly = categoriesRepository.isReadOnly(root),
        parentJob = folderJob,
    )

    private fun photosForScope(root: RootFolder, scope: CategoryScope): List<Photo> {
        val members = categoriesRepository.observeMemberships(root).value[scope.activeCategoryId].orEmpty()
        return scope.slice(photosFor(root), members)
    }

    /**
     * The grid for (root, scope), reused across navigation for the session. A retained instance is
     * returned as-is (only its last-viewed marker is refreshed); the first visit builds and caches
     * one. Reuse is what makes the grid survive a Grid -> Browser -> Grid round trip with its groups,
     * focus and (via the App's held scroll state) scroll position intact.
     */
    fun gridViewModel(
        root: RootFolder,
        scope: CategoryScope,
        lastViewedPhotoId: PhotoId? = null,
    ): GridViewModel {
        val key = GridRetentionKey(root.path, scope)
        retainedGrids[key]?.let { retained ->
            retained.setLastViewed(lastViewedPhotoId)
            return retained
        }
        return GridViewModel(
            root = root,
            allPhotos = photosFor(root),
            categoryScope = scope,
            lastViewedPhotoId = lastViewedPhotoId,
            categories = categoriesRepository,
            exportTxt = exportTxtUseCase,
            copyToFolder = copyPhotosUseCase,
            moveToTrash = movePhotosToTrashUseCase,
            imageLoader = imageLoader,
            captureMetadataSource = captureMetadataSource,
            similarityGrouper = similarityGrouper,
            initialGroupingMode = lastGroupingMode,
            parentJob = folderJob,
            onScrollIndexChanged = { index ->
                appScope.launch { browsePositionRepository.saveIndex(root, index) }
            },
            onGroupingModeChanged = { lastGroupingMode = it },
            onPhotosDeleted = { ids -> removeScannedPhotos(ids) },
        ).also { retainedGrids[key] = it }
    }

    suspend fun resetForNewRoot() {
        // Flush each retained grid's pending scroll save, then drop them: a new root means a new set
        // of grids, and folderJob.cancel below tears their scopes down anyway.
        retainedGrids.values.forEach { it.onClear() }
        retainedGrids.clear()
        _folderJob.cancel()
        _folderJob = SupervisorJob(appScope.coroutineContext[Job])
        categoriesRepository.clearContext()
        imageLoader.evictAll()
        scannedRoot = null
        scannedPhotos = emptyList()
    }

    private companion object {
        // Edge length of the decode feeding the embedder + sharpness scorer. Sized to the learned
        // model's 224px square input so OnnxEmbeddingModel downscales rather than upscales; the
        // classical fallback and the sharpness metric are happy at this size too.
        const val EMBEDDING_EDGE_PX = 224
    }
}
