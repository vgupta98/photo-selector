package com.vishalgupta.photoselector.data.filesystem

import com.vishalgupta.photoselector.domain.format.PhotoFormatRegistry
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.model.ScanProgress
import com.vishalgupta.photoselector.domain.repository.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.name

class FileSystemPhotoRepository(
    private val formatRegistry: PhotoFormatRegistry,
    private val indexPersistence: IndexPersistence? = null,
) : PhotoRepository {

    override fun scan(root: RootFolder): Flow<ScanProgress> = channelFlow {
        val producer = this
        val collected = ArrayList<Photo>(1024)
        val updatedEntries = ArrayList<IndexEntryDto>(1024)
        val cachedIndex = indexPersistence?.read(root)
        var scanned = 0
        try {
            Files.walkFileTree(
                root.path,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(
                        dir: Path,
                        attrs: BasicFileAttributes,
                    ): FileVisitResult {
                        if (dir != root.path && PathFilters.isHiddenOrSystem(dir)) {
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        return if (producer.isActive) FileVisitResult.CONTINUE else FileVisitResult.TERMINATE
                    }

                    override fun visitFile(
                        file: Path,
                        attrs: BasicFileAttributes,
                    ): FileVisitResult {
                        scanned++
                        if (PathFilters.shouldExclude(file, root)) return FileVisitResult.CONTINUE

                        val relative = root.path.relativize(file).toString().replace('\\', '/')
                        val size = attrs.size()
                        val mtime = attrs.lastModifiedTime().toMillis()
                        val cached = cachedIndex?.get(relative)
                        val isPhoto = if (cached != null && cached.size == size && cached.mtimeMs == mtime) {
                            true
                        } else {
                            formatRegistry.isSupported(file)
                        }

                        if (isPhoto) {
                            collected += Photo(
                                id = PhotoId(relative),
                                absolutePath = file,
                                relativePath = relative,
                                fileName = file.name,
                                sizeBytes = size,
                                lastModifiedEpochMs = mtime,
                            )
                            updatedEntries += IndexEntryDto(relPath = relative, size = size, mtimeMs = mtime)
                            if (collected.size % 50 == 0) {
                                producer.trySend(ScanProgress.InProgress(scanned, collected.size))
                            }
                        }

                        return if (producer.isActive) FileVisitResult.CONTINUE else FileVisitResult.TERMINATE
                    }

                    override fun visitFileFailed(
                        file: Path,
                        exc: IOException,
                    ): FileVisitResult = FileVisitResult.CONTINUE
                },
            )
            collected.sortBy { it.relativePath }
            indexPersistence?.write(root, updatedEntries)
            send(ScanProgress.Done(collected))
        } catch (t: Throwable) {
            send(ScanProgress.Failed(t))
        }
    }.flowOn(Dispatchers.IO)
}
