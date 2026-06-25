package com.vishalgupta.photoselector.domain.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppVersionTest {

    @Test fun `parses a three-part version`() {
        assertEquals(AppVersion(1, 7, 3), AppVersion.parseOrNull("1.7.3"))
    }

    @Test fun `pads missing components with zero`() {
        assertEquals(AppVersion(14, 5, 0), AppVersion.parseOrNull("14.5"))
        assertEquals(AppVersion(2, 0, 0), AppVersion.parseOrNull("2"))
    }

    @Test fun `drops a pre-release or build suffix`() {
        assertEquals(AppVersion(1, 7, 0), AppVersion.parseOrNull("1.7.0-beta.2"))
        assertEquals(AppVersion(1, 7, 0), AppVersion.parseOrNull("1.7.0+build9"))
    }

    @Test fun `ignores extra numeric components`() {
        assertEquals(AppVersion(1, 2, 3), AppVersion.parseOrNull("1.2.3.4"))
    }

    @Test fun `returns null for a non-numeric or empty version`() {
        assertNull(AppVersion.parseOrNull(""))
        assertNull(AppVersion.parseOrNull("v-next"))
    }

    @Test fun `orders by major then minor then patch`() {
        assertTrue(AppVersion(1, 7, 0) > AppVersion(1, 6, 9))
        assertTrue(AppVersion(2, 0, 0) > AppVersion(1, 99, 99))
        assertTrue(AppVersion(1, 6, 0) < AppVersion(1, 6, 1))
        assertEquals(0, AppVersion(1, 6, 0).compareTo(AppVersion(1, 6, 0)))
    }

    @Test fun `toString normalises to three parts`() {
        assertEquals("1.7.0", AppVersion.parseOrNull("1.7")!!.toString())
    }

    @Test fun `rollout bucket is in range and stable per id`() {
        val bucket = rolloutBucket("install-abc")
        assertTrue(bucket in 0.0..1.0 && bucket < 1.0)
        assertEquals(bucket, rolloutBucket("install-abc"))
    }
}
