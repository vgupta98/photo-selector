package com.vishalgupta.photoselector.data.ai

/**
 * The per-photo signals similarity grouping needs, both derived from the decoded thumbnail and
 * cached together (they are computed in the same decode pass):
 *
 *  - [embedding]: an L2-normalized vector. Cosine similarity between two embeddings reduces to a
 *    dot product, which is how [com.vishalgupta.photoselector.domain.grouping.SimilarityGrouper]
 *    decides whether two frames are the same shot.
 *  - [sharpness]: a higher-is-sharper scalar (variance of Laplacian). Used only to pick the
 *    suggested-keeper frame within a cluster — a hint, never a verdict.
 */
class PhotoFeatures(
    val embedding: FloatArray,
    val sharpness: Float,
)
