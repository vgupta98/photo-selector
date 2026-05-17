package com.vishalgupta.photoselector.domain.model

sealed interface ScanProgress {
    data class InProgress(val scanned: Int, val foundPhotos: Int) : ScanProgress
    data class Done(val photos: List<Photo>) : ScanProgress
    data class Failed(val error: Throwable) : ScanProgress
}
