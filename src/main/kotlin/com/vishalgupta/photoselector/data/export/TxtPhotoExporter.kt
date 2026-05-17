package com.vishalgupta.photoselector.data.export

import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.RootFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class TxtPhotoExporter {
    suspend fun export(
        @Suppress("UNUSED_PARAMETER") root: RootFolder,
        favourites: List<Photo>,
        destinationTxt: Path,
    ) {
        withContext(Dispatchers.IO) {
            val payload = favourites.joinToString(separator = "\n", postfix = "\n") { it.relativePath }
            Files.write(
                destinationTxt,
                payload.toByteArray(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
        }
    }
}
