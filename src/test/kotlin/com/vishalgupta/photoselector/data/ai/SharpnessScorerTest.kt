package com.vishalgupta.photoselector.data.ai

import com.vishalgupta.photoselector.data.format.SkiaImageDecoding
import com.vishalgupta.photoselector.testing.ImageFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharpnessScorerTest {

    @Test fun `a high-frequency checker scores far sharper than a flat fill`() {
        val sharp = SharpnessScorer.score(ImageFixtures.checker(32, 32))
        val flat = SharpnessScorer.score(ImageFixtures.solid(32, 32))
        assertTrue(sharp > flat, "checker $sharp should exceed flat $flat")
        assertEquals(0f, flat, "a flat image has no edges")
    }

    @Test fun `a soft ramp sits between flat and a hard checker`() {
        val flat = SharpnessScorer.score(ImageFixtures.solid(32, 32))
        val ramp = SharpnessScorer.score(ImageFixtures.ramp(32, 32))
        val checker = SharpnessScorer.score(ImageFixtures.checker(32, 32))
        assertTrue(ramp >= flat)
        assertTrue(ramp < checker, "a gentle ramp is softer than a 1px checker")
    }

    @Test fun `an image too small to convolve scores zero rather than throwing`() {
        assertEquals(0f, SharpnessScorer.score(ImageFixtures.checker(2, 2)))
    }

    @Test fun `on a common canvas a detailed frame outscores a low-res copy upscaled to match`() {
        // The cluster bug: the same shot at two resolutions. Scored at their native sizes the small
        // one wins (steeper per-pixel edges); scored on a shared canvas the detailed one must win.
        val target = 64
        val detailed = SharpnessScorer.score(ImageFixtures.checker(target, target))
        val lowResUpscaled = SharpnessScorer.score(
            SkiaImageDecoding.scaleUpToLongEdge(ImageFixtures.checker(4, 4), target),
        )
        assertTrue(
            detailed > lowResUpscaled,
            "a native ${target}px checker ($detailed) must beat a 4px checker upscaled to $target ($lowResUpscaled)",
        )
    }
}
