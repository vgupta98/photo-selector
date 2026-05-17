package com.vishalgupta.photoselector.domain.format

import java.nio.file.Path
import kotlin.io.path.extension

interface PhotoFormat {
    val id: String
    val extensions: Set<String>
    fun matches(path: Path): Boolean = path.extension.lowercase() in extensions
}
