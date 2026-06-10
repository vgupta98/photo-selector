package com.vishalgupta.photoselector.data.ai

import com.vishalgupta.photoselector.domain.model.DecodedImage
import com.vishalgupta.photoselector.domain.model.Photo

/**
 * Produces (and memoizes to disk) the [PhotoFeatures] for a photo: a disk-cache hit short-circuits;
 * a miss decodes the thumbnail once and derives both the embedding and the sharpness from that same
 * pass before caching them together. Decoding is the cost, so it happens at most once per photo per
 * (model, source) version.
 *
 * [decode] is injected rather than depending on the whole image stack, so the extractor reuses the
 * existing decode/thumbnail path in production and a deterministic synthetic image in tests. It
 * returns null when a photo can't be decoded (an unsupported format, a read error); such a photo
 * has no features and [com.vishalgupta.photoselector.domain.grouping.SimilarityGrouper] leaves it
 * ungrouped rather than guessing.
 */
class PhotoFeatureExtractor(
    private val model: EmbeddingModel,
    private val cache: EmbeddingCache,
    private val decode: suspend (Photo) -> DecodedImage?,
) {
    suspend fun featuresFor(photo: Photo): PhotoFeatures? {
        cache.get(photo)?.let { return it }
        val image = decode(photo) ?: return null
        val features = PhotoFeatures(
            embedding = model.embed(image),
            sharpness = SharpnessScorer.score(image),
        )
        cache.put(photo, features)
        return features
    }
}
