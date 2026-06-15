package com.vishalgupta.photoselector.presentation.grid

import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.foundation.v2.maxScrollOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression guard for the v1.5.0 grid crash: while the grid reshapes under `animateItem` (the
 * Similarity lens collapsing singles into burst tiles), the lazy-grid scrollbar adapter can emit a
 * NaN content size / scroll offset, which `VerticalScrollbar` rounds mid-measure and throws
 * `IllegalArgumentException: Cannot round NaN value`. [NanSafeScrollbarAdapter] must turn those
 * non-finite reads into finite values so the scrollbar never sees a NaN to round.
 */
class NanSafeScrollbarAdapterTest {

    /** A stand-in for the real lazy-grid adapter whose getters can return any (even NaN) value. */
    private class FakeAdapter(
        override val scrollOffset: Double,
        override val contentSize: Double,
        override val viewportSize: Double,
    ) : ScrollbarAdapter {
        override suspend fun scrollTo(scrollOffset: Double) = Unit
    }

    @Test
    fun `sanitises NaN reads to finite values`() {
        val safe = NanSafeScrollbarAdapter(
            FakeAdapter(
                scrollOffset = Double.NaN,
                contentSize = Double.NaN,
                viewportSize = Double.NaN,
            ),
        )

        assertEquals(0.0, safe.scrollOffset)
        assertEquals(0.0, safe.contentSize)
        assertEquals(0.0, safe.viewportSize)
        // maxScrollOffset is the derived value the scrollbar actually rounds; it must stay finite.
        assertTrue(safe.maxScrollOffset.isFinite())
    }

    @Test
    fun `sanitises infinite reads to finite values`() {
        val safe = NanSafeScrollbarAdapter(
            FakeAdapter(
                scrollOffset = Double.POSITIVE_INFINITY,
                contentSize = Double.NEGATIVE_INFINITY,
                viewportSize = Double.POSITIVE_INFINITY,
            ),
        )

        assertTrue(safe.scrollOffset.isFinite())
        assertTrue(safe.contentSize.isFinite())
        assertTrue(safe.viewportSize.isFinite())
    }

    @Test
    fun `passes finite reads through untouched`() {
        val safe = NanSafeScrollbarAdapter(
            FakeAdapter(scrollOffset = 120.0, contentSize = 4000.0, viewportSize = 800.0),
        )

        assertEquals(120.0, safe.scrollOffset)
        assertEquals(4000.0, safe.contentSize)
        assertEquals(800.0, safe.viewportSize)
    }
}
