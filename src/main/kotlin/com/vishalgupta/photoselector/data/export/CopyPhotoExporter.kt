package com.vishalgupta.photoselector.data.export

import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.repository.ConflictPolicy
import com.vishalgupta.photoselector.domain.repository.CopyReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

class CopyPhotoExporter {

    suspend fun copy(
        @Suppress("UNUSED_PARAMETER") root: RootFolder,
        favourites: List<Photo>,
        destDir: Path,
        policy: ConflictPolicy,
        onProgress: (copied: Int, total: Int) -> Unit,
    ): CopyReport = withContext(Dispatchers.IO) {
        Files.createDirectories(destDir)
        var copied = 0
        var skipped = 0
        val failed = ArrayList<Pair<Photo, Throwable>>()
        val total = favourites.size

        for ((index, photo) in favourites.withIndex()) {
            // Cooperative cancellation: ensureActive() throws if the scope was cancelled, so a
            // cancelled bulk copy stops at the next file boundary rather than copying every remaining
            // file. (Files.copy isn't interruptible here — no runInterruptible — so any in-flight file
            // simply finishes first.)
            ensureActive()
            try {
                val target = resolveTarget(destDir, photo, policy)
                if (target == null) {
                    skipped++
                } else {
                    Files.createDirectories(target.parent)
                    when (policy) {
                        ConflictPolicy.OVERWRITE -> Files.copy(
                            photo.absolutePath,
                            target,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES,
                        )
                        ConflictPolicy.SKIP, ConflictPolicy.RENAME ->
                            Files.copy(photo.absolutePath, target, StandardCopyOption.COPY_ATTRIBUTES)
                    }
                    copied++
                }
            } catch (ce: CancellationException) {
                // Defensive and consistent with the other call sites; this try body has no suspend
                // call, so it can't actually originate a CancellationException today. ensureActive()
                // above is what does the real cancellation work.
                throw ce
            } catch (t: Throwable) {
                failed += photo to t
            }
            onProgress(index + 1, total)
        }

        CopyReport(copied = copied, skipped = skipped, failed = failed)
    }

    private fun resolveTarget(destDir: Path, photo: Photo, policy: ConflictPolicy): Path? {
        val direct = destDir.resolve(photo.relativePath)
        if (!Files.exists(direct)) return direct
        return when (policy) {
            ConflictPolicy.OVERWRITE -> direct
            ConflictPolicy.SKIP -> null
            ConflictPolicy.RENAME -> findRenamed(direct)
        }
    }

    private fun findRenamed(target: Path): Path {
        val parent = target.parent
        val base = target.nameWithoutExtension
        val ext = target.extension.let { if (it.isEmpty()) "" else ".$it" }
        var i = 2
        while (true) {
            val candidate = parent.resolve("$base ($i)$ext")
            if (!Files.exists(candidate)) return candidate
            i++
        }
    }
}
