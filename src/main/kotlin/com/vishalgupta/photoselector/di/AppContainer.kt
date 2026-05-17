package com.vishalgupta.photoselector.di

import com.vishalgupta.photoselector.data.export.CompositePhotoExporter
import com.vishalgupta.photoselector.data.export.CopyPhotoExporter
import com.vishalgupta.photoselector.data.export.TxtPhotoExporter
import com.vishalgupta.photoselector.data.favourites.JsonFavouritesRepository
import com.vishalgupta.photoselector.data.filesystem.FileSystemPhotoRepository
import com.vishalgupta.photoselector.data.format.DefaultPhotoFormatRegistry
import com.vishalgupta.photoselector.data.format.JpegDecoder
import com.vishalgupta.photoselector.data.format.PngDecoder
import com.vishalgupta.photoselector.data.image.ImageLoader
import com.vishalgupta.photoselector.data.image.SkikoImageLoader
import com.vishalgupta.photoselector.domain.format.PhotoFormatRegistry
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.repository.FavouritesRepository
import com.vishalgupta.photoselector.domain.repository.PhotoExporter
import com.vishalgupta.photoselector.domain.repository.PhotoRepository
import com.vishalgupta.photoselector.domain.usecase.CopyFavouritesToFolderUseCase
import com.vishalgupta.photoselector.domain.usecase.ExportFavouritesTxtUseCase
import com.vishalgupta.photoselector.domain.usecase.ObserveFavouritesUseCase
import com.vishalgupta.photoselector.domain.usecase.ScanRootFolderUseCase
import com.vishalgupta.photoselector.domain.usecase.ToggleFavouriteUseCase
import com.vishalgupta.photoselector.presentation.browser.BrowserViewModel
import com.vishalgupta.photoselector.presentation.favourites.FavouritesViewModel
import com.vishalgupta.photoselector.presentation.navigation.Screen
import com.vishalgupta.photoselector.presentation.rootpicker.RootFolderPickerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json

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

    val imageLoader: ImageLoader = SkikoImageLoader(
        registry = formatRegistry,
        decodeDispatcher = imageDecodeDispatcher,
    )

    private val photoRepository: PhotoRepository = FileSystemPhotoRepository(formatRegistry)
    private val favouritesRepository: FavouritesRepository = JsonFavouritesRepository(json)
    private val exporter: PhotoExporter = CompositePhotoExporter(TxtPhotoExporter(), CopyPhotoExporter())

    private val scanUseCase = ScanRootFolderUseCase(photoRepository)
    private val observeFavouritesUseCase = ObserveFavouritesUseCase(favouritesRepository)
    private val toggleFavouriteUseCase = ToggleFavouriteUseCase(favouritesRepository)
    private val exportTxtUseCase = ExportFavouritesTxtUseCase(exporter)
    private val copyFavouritesUseCase = CopyFavouritesToFolderUseCase(exporter)

    val currentScreen = MutableStateFlow<Screen>(Screen.RootPicker)

    private var scannedPhotos: List<Photo> = emptyList()
    private var scannedRoot: RootFolder? = null

    fun photosFor(root: RootFolder): List<Photo> =
        if (scannedRoot?.path == root.path) scannedPhotos else emptyList()

    fun setScanResult(root: RootFolder, photos: List<Photo>) {
        scannedRoot = root
        scannedPhotos = photos
    }

    fun goTo(screen: Screen) {
        currentScreen.value = screen
    }

    fun rootPickerViewModel(): RootFolderPickerViewModel = RootFolderPickerViewModel(
        scanRootFolder = scanUseCase,
        onScanComplete = { root, photos ->
            imageLoader.evictAll()
            setScanResult(root, photos)
            goTo(Screen.Browser(root))
        },
    )

    fun browserViewModel(root: RootFolder, initialIndex: Int): BrowserViewModel = BrowserViewModel(
        root = root,
        photos = photosFor(root),
        initialIndex = initialIndex,
        observeFavourites = observeFavouritesUseCase,
        toggleFavourite = toggleFavouriteUseCase,
        imageLoader = imageLoader,
        isReadOnly = favouritesRepository.isReadOnly(root),
    )

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
