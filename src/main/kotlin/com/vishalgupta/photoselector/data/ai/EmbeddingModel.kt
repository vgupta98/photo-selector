package com.vishalgupta.photoselector.data.ai

import com.vishalgupta.photoselector.domain.model.DecodedImage

/**
 * Turns a decoded thumbnail into a fixed-length embedding vector whose cosine distance encodes
 * visual similarity. The default on-device implementation is the dependency-free, classical
 * [DownscaleGrayEmbeddingModel]; a future PR swaps in a learned (CLIP/DINO-style) model exported
 * to ONNX behind this same interface, which is also what lets tests inject a deterministic fake.
 *
 * Implementations must be pure and never throw — a decode that produced a [DecodedImage] always
 * yields a vector of length [dimensions].
 */
interface EmbeddingModel {

    /**
     * Stable identity of this model + its parameters, folded into the embedding cache key so a
     * model swap or parameter change invalidates stale vectors automatically. Bump it whenever the
     * produced vectors change meaning.
     */
    val id: String

    /** Length of every vector [embed] returns. */
    val dimensions: Int

    /** Embeds [image] into an L2-normalized vector of length [dimensions]. */
    fun embed(image: DecodedImage): FloatArray
}
