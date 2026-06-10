package com.vishalgupta.photoselector.testing

import com.vishalgupta.photoselector.data.ai.EmbeddingModel
import com.vishalgupta.photoselector.domain.model.DecodedImage

/**
 * A deterministic [EmbeddingModel] for tests: the embedding is whatever [embedder] returns, so a
 * test controls exactly which frames look alike. Shared fake — reuse it rather than re-declaring a
 * private one (see the project's testing conventions).
 */
class FakeEmbeddingModel(
    override val id: String = "fake-v1",
    override val dimensions: Int = 4,
    private val embedder: (DecodedImage) -> FloatArray,
) : EmbeddingModel {
    override fun embed(image: DecodedImage): FloatArray = embedder(image)
}
