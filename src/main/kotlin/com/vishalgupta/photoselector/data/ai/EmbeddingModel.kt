package com.vishalgupta.photoselector.data.ai

import com.vishalgupta.photoselector.domain.model.DecodedImage

/**
 * Turns a decoded thumbnail into a fixed-length embedding vector whose cosine distance encodes
 * visual similarity. The default on-device implementation is the dependency-free, classical
 * [DownscaleGrayEmbeddingModel]; a future PR swaps in a learned (CLIP/DINO-style) model exported
 * to ONNX behind this same interface, which is also what lets tests inject a deterministic fake.
 *
 * Implementations must be pure and never throw. The success path yields a vector of length
 * [dimensions]; an inference *failure* returns `null` (never an exception, never an in-band sentinel
 * vector) so the caller can tell "this frame is genuinely featureless" apart from "we failed to look
 * at it" and decline to cache the latter — see [PhotoFeatureExtractor].
 */
interface EmbeddingModel {

    /**
     * Stable identity of this model + its parameters, folded into the embedding cache key so a
     * model swap or parameter change invalidates stale vectors automatically. Bump it whenever the
     * produced vectors change meaning.
     */
    val id: String

    /** Length of every vector a successful [embed] returns. */
    val dimensions: Int

    /**
     * Embeds [image] into an L2-normalized vector of length [dimensions], or returns `null` if
     * inference failed. A `null` is a *transient* signal — the caller leaves the frame ungrouped for
     * this pass and re-embeds next time rather than persisting the failure.
     */
    fun embed(image: DecodedImage): FloatArray?
}
