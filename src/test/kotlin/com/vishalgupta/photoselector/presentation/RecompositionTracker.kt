package com.vishalgupta.photoselector.presentation

/**
 * Test-only recomposition counter. Call [record] from the body of the composable
 * scope you want to measure, then read [get] after driving a state change to
 * assert whether that scope actually recomposed.
 *
 * Backed by a plain (non-snapshot) map on purpose: recording a count must not
 * itself register a snapshot read/write, or it would invalidate the very scope
 * we are measuring. Incrementing during composition is otherwise a side-effect to
 * avoid in production — fine here because the tracker exists only to observe it.
 *
 * The one rule that makes counts meaningful: [record] must sit directly in the
 * body of a restartable composable, with no inner composable-lambda scope between
 * it and the recomposition you care about. A `@Composable () -> Unit` you invoke
 * gets its *own* restart scope, so a wrapper that takes a content lambda and
 * records in the wrapper body measures the wrapper's input changes, not the
 * content's recomposition. Wrap the component in a thin counted composable whose
 * params mirror it instead (see GridRecompositionTest.CountedThumbnail).
 */
class RecompositionTracker {
    private val counts = mutableMapOf<String, Int>()

    fun record(tag: String) {
        counts[tag] = (counts[tag] ?: 0) + 1
    }

    /** How many times the scope tagged [tag] has (re)composed. */
    operator fun get(tag: String): Int = counts[tag] ?: 0
}
