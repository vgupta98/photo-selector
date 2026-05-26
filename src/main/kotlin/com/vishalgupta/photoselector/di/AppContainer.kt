package com.vishalgupta.photoselector.di

import com.vishalgupta.photoselector.data.browse.JsonBrowsePositionRepository
import com.vishalgupta.photoselector.data.export.CompositePhotoExporter
import com.vishalgupta.photoselector.data.export.CopyPhotoExporter
import com.vishalgupta.photoselector.data.export.TxtPhotoExporter
import com.vishalgupta.photoselector.data.favourites.JsonFavouritesRepository
import com.vishalgupta.photoselector.data.filesystem.FileSystemPhotoRepository
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
import com.vishalgupta.photoselector.domain.repository.FavouritesRepository
import com.vishalgupta.photoselector.domain.repository.BrowsePositionRepository
import com.vishalgupta.photoselector.domain.repository.PhotoExporter
import com.vishalgupta.photoselector.domain.repository.PhotoRepository
import com.vishalgupta.photoselector.domain.usecase.CopyFavouritesToFolderUseCase
import com.vishalgupta.photoselector.domain.usecase.ExportFavouritesTxtUseCase
import com.vishalgupta.photoselector.domain.usecase.ObserveFavouritesUseCase
import com.vishalgupta.photoselector.domain.usecase.ScanRootFolderUseCase
import com.vishalgupta.photoselector.domain.usecase.ToggleFavouriteUseCase
import com.vishalgupta.photoselector.presentation.browser.BrowserViewModel
import com.vishalgupta.photoselector.presentation.favourites.FavouritesViewModel
import com.vishalgupta.photoselector.presentation.navigation.BrowseScope
import com.vishalgupta.photoselector.presentation.common.MacSystemActions
import com.vishalgupta.photoselector.presentation.common.SystemActions
import com.vishalgupta.photoselector.presentation.navigation.Screen
import com.vishalgupta.photoselector.presentation.rootpicker.RootFolderPickerViewModel
import kotlinx.coroutines.Dispatchers
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

    private val formatRegistry: PhotoFormatRegistry = DefaultPhotoFormatRegistry(
        decoders = listOf(JpegDecoder(), PngDecoder()),
    )

    private val diskThumbnailCache = DiskThumbnailCache(
        cacheDir = Path.of(System.getProperty("user.home"), "Library", "Caches", "PhotoSelector"),
    ).also { it.startEviction(imageDecodeDispatcher) }

    val imageLoader: ImageLoader = SkikoImageLoader(
        registry = formatRegistry,
        decodeDispatcher = imageDecodeDispatcher,
        diskCache = diskThumbnailCache,
    )

    private val photoRepository: PhotoRepository = FileSystemPhotoRepository(formatRegistry)
    private val favouritesRepository: FavouritesRepository = JsonFavouritesRepository(json)
    private val browsePositionRepository: BrowsePositionRepository = JsonBrowsePositionRepository(json)
    private val exporter: PhotoExporter = CompositePhotoExporter(TxtPhotoExporter(), CopyPhotoExporter())

    private val scanUseCase = ScanRootFolderUseCase(photoRepository)
    private val observeFavouritesUseCase = ObserveFavouritesUseCase(favouritesRepository)
    private val toggleFavouriteUseCase = ToggleFavouriteUseCase(favouritesRepository)
    private val exportTxtUseCase = ExportFavouritesTxtUseCase(exporter)
    private val copyFavouritesUseCase = CopyFavouritesToFolderUseCase(exporter)

    val systemActions: SystemActions = MacSystemActions()

    val currentScreen = MutableStateFlow<Screen>(Screen.RootPicker)
    val currentPhotoPath = MutableStateFlow<Path?>(null)

    private var scannedPhotos: List<Photo> = emptyList()
    private var scannedRoot: RootFolder? = null

    fun photosFor(root: RootFolder): List<Photo> =
        if (scannedRoot?.path == root.path) scannedPhotos else emptyList()

    fun loadBrowsePosition(root: RootFolder): Int = browsePositionRepository.load(root)

    /** Returns the position of [id] within the current favourites slice (sorted by relativePath). */
    fun favouritesIndexOf(root: RootFolder, id: PhotoId): Int =
        photosForScope(root, BrowseScope.FavouritesOnly)
            .indexOfFirst { it.id == id }
            .coerceAtLeast(0)

    fun setScanResult(root: RootFolder, photos: List<Photo>) {
        scannedRoot = root
        scannedPhotos = photos
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
            val savedIndex = browsePositionRepository.load(root)
            goTo(Screen.Browser(root, initialIndex = savedIndex))
        },
    )

    fun browserViewModel(
        root: RootFolder,
        initialIndex: Int,
        scope: BrowseScope,
    ): BrowserViewModel = BrowserViewModel(
        root = root,
        photos = photosForScope(root, scope),
        initialIndex = initialIndex,
        observeFavourites = observeFavouritesUseCase,
        toggleFavourite = toggleFavouriteUseCase,
        imageLoader = imageLoader,
        isReadOnly = favouritesRepository.isReadOnly(root),
        onPositionChanged = when (scope) {
            BrowseScope.AllPhotos -> { index -> browsePositionRepository.save(root, index) }
            BrowseScope.FavouritesOnly -> null
        },
    )

    private fun photosForScope(root: RootFolder, scope: BrowseScope): List<Photo> {
        val all = photosFor(root)
        return when (scope) {
            BrowseScope.AllPhotos -> all
            BrowseScope.FavouritesOnly -> {
                val favIds = favouritesRepository.observe(root).value
                all.filter { it.id in favIds }
            }
        }
    }

    fun favouritesViewModel(root: RootFolder): FavouritesViewModel = FavouritesViewModel(
        root = root,
        allPhotos = photosFor(root),
        observeFavourites = observeFavouritesUseCase,
        exportTxt = exportTxtUseCase,
        copyToFolder = copyFavouritesUseCase,
        imageLoader = imageLoader,
    )

    suspend fun resetForNewRoot() {
        favouritesRepository.clearContext()
        imageLoader.evictAll()
        scannedRoot = null
        scannedPhotos = emptyList()
    }
}
