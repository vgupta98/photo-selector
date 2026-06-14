package com.vishalgupta.photoselector.presentation.inspect

import com.vishalgupta.photoselector.presentation.StateHolder
import com.vishalgupta.photoselector.presentation.browser.BrowserViewModel
import com.vishalgupta.photoselector.presentation.survey.SurveyViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Inspect's two presentations of the same fixed photo set. */
enum class InspectMode { Grid, Browse }

/**
 * Coordinates Inspect: one fixed set of photos shown either as the overview *grid* (the
 * [SurveyViewModel] facet) or in full-screen *browse* (the [BrowserViewModel] facet, walking only
 * this set). It owns the [mode] toggle and the single shared cursor — the active position within the
 * set — so flipping modes lands on the same photo either way.
 *
 * The two facets are *reused wholesale*: their decode, pinning, filing and category wiring already do
 * exactly what each mode needs, so Inspect adds only the coordination. Each facet is built lazily via
 * the injected factories — a grid-first set never builds the browser until the first toggle, and a
 * browse-only set ([gridAvailable] false, e.g. a long burst past the grid cap) never builds the grid
 * at all, which is what keeps a large set from pinning dozens of decodes. Inspect owns the facets'
 * lifecycle ([onClear] cascades), so the hosting screens run them with `manageLifecycle = false` and
 * a mode toggle doesn't dispose the hidden facet's decoded frames.
 */
class InspectViewModel(
    // Null when the set is too large for the grid (browse-only); non-null builds the grid facet,
    // seeded at the shared cursor. Browse is always available.
    private val makeGrid: ((initialActive: Int) -> SurveyViewModel)?,
    private val makeBrowse: (initialIndex: Int) -> BrowserViewModel,
    initialMode: InspectMode,
    parentJob: Job? = null,
) : StateHolder(parentJob) {

    val gridAvailable: Boolean = makeGrid != null

    private val _mode = MutableStateFlow(initialMode)
    val mode: StateFlow<InspectMode> = _mode.asStateFlow()

    // The single source of truth for "which photo", as a position in the inspected set. Whichever
    // facet is shown is seeded from it; on a toggle it's refreshed from the facet being left.
    private var activeIndex: Int = 0

    private var gridVmOrNull: SurveyViewModel? = null
    private var browseVmOrNull: BrowserViewModel? = null

    // gridViewModel()/browseViewModel() are called from InspectScreen's composition to hand the live
    // facet to the child screen. First call constructs the facet; the result is cached in a plain (non
    // snapshot) field, so the construction is intentionally side-effecting but idempotent — reads after
    // the first are pure, it triggers no recomposition, and an abandoned composition is still cleared
    // because onClear() cascades to whatever was built. Keep these read-mostly; do the actual mode flip
    // (which seeds the cursor) in openBrowse()/openGrid(), not here.

    /** The grid facet, built (and seeded at the shared cursor) on first use. Only call when [gridAvailable]. */
    fun gridViewModel(): SurveyViewModel =
        gridVmOrNull ?: requireNotNull(makeGrid) { "grid is unavailable for this set" }
            .invoke(activeIndex)
            .also { gridVmOrNull = it }

    /** The browse facet, built (and seeded at the shared cursor) on first use. */
    fun browseViewModel(): BrowserViewModel =
        browseVmOrNull ?: makeBrowse(activeIndex).also { browseVmOrNull = it }

    /** Grid -> Browse: carry the active tile across so browse opens on the same photo. */
    fun openBrowse() {
        gridVmOrNull?.let { activeIndex = it.state.value.activeTile }
        browseVmOrNull?.jumpTo(activeIndex)
        _mode.value = InspectMode.Browse
    }

    /** Browse -> Grid (a no-op when the set is browse-only); carry the current photo back to the tile. */
    fun openGrid() {
        if (!gridAvailable) return
        browseVmOrNull?.let { activeIndex = it.state.value.currentIndex }
        gridVmOrNull?.setActive(activeIndex)
        _mode.value = InspectMode.Grid
    }

    /** The active position in the set, read from whichever facet is live — for restoring the source on exit. */
    fun activeSubsetIndex(): Int = when (_mode.value) {
        InspectMode.Grid -> gridVmOrNull?.state?.value?.activeTile ?: activeIndex
        InspectMode.Browse -> browseVmOrNull?.state?.value?.currentIndex ?: activeIndex
    }

    override fun onClear() {
        gridVmOrNull?.onClear()
        browseVmOrNull?.onClear()
        super.onClear()
    }
}
