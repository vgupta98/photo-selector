package com.vishalgupta.photoselector.di

import com.vishalgupta.photoselector.data.browse.JsonBrowsePositionRepository
import com.vishalgupta.photoselector.data.export.CompositePhotoExporter
import com.vishalgupta.photoselector.data.export.CopyPhotoExporter
import com.vishalgupta.photoselector.data.export.TxtPhotoExporter
import com.vishalgupta.photoselector.data.categories.JsonCategoriesRepository
import com.vishalgupta.photoselector.data.filesystem.FileSystemPhotoRepository
import com.vishalgupta.photoselector.data.trash.DesktopPhotoTrash
import com.vishalgupta.photoselector.data.format.DefaultPhotoFormatRegistry
import com.vishalgupta.photoselector.data.format.JpegDecoder
import com.vishalgupta.photoselector.data.format.PngDecoder
import com.vishalgupta.photoselector.data.image.DiskThumbnailCache
import com.vishalgupta.photoselector.data.image.ImageLoader
import com.vishalgupta.photoselector.data.image.SkikoImageLoader
import com.vishalgupta.photoselector.domain.format.PhotoFormatRegistry
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
import com.vishalgupta.photoselector.presentation.navigation.activeCategoryId
import com.vishalgupta.photoselector.presentation.navigation.slice
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
        decoders = listOf(JpegDecoder(), PngDecoder()),
    )

    private val diskThumbnailCache = DiskThumbnailCache(
        cacheDir = Path.of(System.getProperty("user.home"), "Library", "Caches", "PhotoSelector"),
    ).also { it.startEviction(appScope) }

    val imageLoader: ImageLoader = SkikoImageLoader(
        registry = formatRegistry,
        decodeDispatcher = imageDecodeDispatcher,
        diskCache = diskThumbnailCache,
    )

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

    fun gridViewModel(
        root: RootFolder,
        scope: CategoryScope,
        lastViewedPhotoId: PhotoId? = null,
    ): GridViewModel = GridViewModel(
        root = root,
        allPhotos = photosFor(root),
        categoryScope = scope,
        lastViewedPhotoId = lastViewedPhotoId,
        categories = categoriesRepository,
        exportTxt = exportTxtUseCase,
        copyToFolder = copyPhotosUseCase,
        moveToTrash = movePhotosToTrashUseCase,
        imageLoader = imageLoader,
        parentJob = folderJob,
        onScrollIndexChanged = { index ->
            appScope.launch { browsePositionRepository.saveIndex(root, index) }
        },
        onPhotosDeleted = { ids -> removeScannedPhotos(ids) },
    )

    suspend fun resetForNewRoot() {
        _folderJob.cancel()
        _folderJob = SupervisorJob(appScope.coroutineContext[Job])
        categoriesRepository.clearContext()
        imageLoader.evictAll()
        scannedRoot = null
        scannedPhotos = emptyList()
    }
}
