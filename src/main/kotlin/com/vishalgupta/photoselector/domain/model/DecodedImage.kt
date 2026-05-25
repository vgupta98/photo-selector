package com.vishalgupta.photoselector.domain.model

class DecodedImage(
    val width: Int,
    val height: Int,
    val bgraBytes: ByteArray,
) {
    val byteSize: Long get() = bgraBytes.size.toLong()
}
