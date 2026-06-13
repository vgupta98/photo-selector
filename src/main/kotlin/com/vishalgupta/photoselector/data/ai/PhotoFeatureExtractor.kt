package com.vishalgupta.photoselector.data.ai

import com.vishalgupta.photoselector.domain.model.DecodedImage
import com.vishalgupta.photoselector.domain.model.Photo

/**
 * Produces (and memoizes to disk) the [PhotoFeatures] for a photo: a disk-cache hit short-circuits;
 * a miss decodes the frame and derives the embedding and the sharpness before caching them together.
 * Decoding is the cost, so each decode happens at most once per photo per (model, source) version.
 *
 * The two derivations want *different* decodes, which is why there are two decode lambdas. The
 * embedder squashes its input to a 224px square, so [decodeForEmbedding] hands it a small thumbnail.
 * Sharpness (Laplacian variance) is the opposite: a few-pixel focus/motion blur is sub-pixel once a
 * frame is shrunk to 224, so adjacent burst frames score almost identically there and the "sharpest"
 * pick degrades to noise — hence [decodeForSharpness] decodes at a higher resolution where the blur
 * still registers. It also normalises every frame onto one fixed canonical canvas (see
 * `AppContainer`): variance-of-Laplacian is per-pixel, so without that a *lower*-resolution copy of
 * the same shot scores higher (steeper pixel-grid edges) and wrongly wins the key-frame pick.
 * Sharing one 224px decode (the original wiring) is exactly what made the suggested key frame look
 * wrong.
 *
 * Both decodes are injected rather than depending on the whole image stack, so the extractor reuses
 * the existing decode/thumbnail path in production and deterministic synthetic images in tests.
 * [decodeForEmbedding] returning null means the photo can't be decoded at all (unsupported format, a
 * read error): it has no features and
 * [com.vishalgupta.photoselector.domain.grouping.SimilarityGrouper] leaves it ungrouped rather than
 * guessing. [decodeForSharpness] returning null is softer — the high-res decode failed but the frame
 * is otherwise fine — so it falls back to the embedding image, yielding a weaker (224px) score
 * instead of dropping the frame out of the sharpness map.
 */
class PhotoFeatureExtractor(
    private val model: EmbeddingModel,
    private val cache: EmbeddingCache,
    private val decodeForEmbedding: suspend (Photo) -> DecodedImage?,
    private val decodeForSharpness: suspend (Photo) -> DecodedImage?,
) {
    suspend fun featuresFor(photo: Photo): PhotoFeatures? {
        cache.get(photo)?.let { return it }
        val embedImage = decodeForEmbedding(photo) ?: return null
        val sharpnessImage = decodeForSharpness(photo) ?: embedImage
        val features = PhotoFeatures(
            embedding = model.embed(embedImage),
            sharpness = SharpnessScorer.score(sharpnessImage),
        )
        cache.put(photo, features)
        return features
    }
}
