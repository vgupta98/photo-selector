package com.vishalgupta.photoselector.domain.model

class DecodedImage(
    val width: Int,
    val height: Int,
    val argbPixels: IntArray,
) {
    val byteSize: Long get() = width.toLong() * height.toLong() * 4L
}
