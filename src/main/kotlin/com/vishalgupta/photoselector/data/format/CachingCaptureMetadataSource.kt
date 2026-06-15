package com.vishalgupta.photoselector.data.format

import com.vishalgupta.photoselector.domain.grouping.CaptureMetadata
import com.vishalgupta.photoselector.domain.grouping.CaptureMetadataSource
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import java.util.concurrent.ConcurrentHashMap

/**
 * Memoizes a [CaptureMetadataSource] by [PhotoId] so the EXIF for each photo is read from disk at
 * most once per session. Burst grouping re-runs whenever the visible slice changes (a category
 * toggle re-slices); without this every re-group would re-open every file. Thread-safe — grouping
 * runs off the UI thread and several grids may share one instance.
 */
class CachingCaptureMetadataSource(
    private val delegate: CaptureMetadataSource,
) : CaptureMetadataSource {

    private val cache = ConcurrentHashMap<PhotoId, CaptureMetadata>()

    override fun metadataFor(photo: Photo): CaptureMetadata =
        cache.getOrPut(photo.id) { delegate.metadataFor(photo) }
}
