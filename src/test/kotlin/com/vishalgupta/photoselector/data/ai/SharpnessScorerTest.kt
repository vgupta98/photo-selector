package com.vishalgupta.photoselector.data.ai

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
}
