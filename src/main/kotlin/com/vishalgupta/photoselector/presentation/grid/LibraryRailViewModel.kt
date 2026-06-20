package com.vishalgupta.photoselector.presentation.grid

import com.vishalgupta.photoselector.domain.model.Category
import com.vishalgupta.photoselector.domain.model.CategoryId
import com.vishalgupta.photoselector.domain.model.RootFolder
import com.vishalgupta.photoselector.domain.repository.CategoriesRepository
import com.vishalgupta.photoselector.presentation.StateHolder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing

/**
 * Backs the [com.vishalgupta.photoselector.presentation.designsystem.organism.LibraryRail], which
 * is root-level chrome: it lists every scope (Favourites + custom categories with their counts) and
 * owns category create / rename / delete. Deliberately scoped to the **root**, not the grid's
 * (root, scope): the rail is the same for every scope, so the navigation host ([App]) mounts it
 * *outside* the per-scope `key(GridRetentionKey)` and it survives a category switch untouched (a
 * grid-scoped rail used to tear down and rebuild on every switch, flickering).
 *
 * [entries] mirrors what the grid derives for its tile badges because both read the *same* two
 * repository flows ([CategoriesRepository.observeCategories] / [observeMemberships]) — so a rail
 * count and a tile badge can never disagree. CRUD just forwards to the repository on [scope]; the
 * grid's own [GridViewModel] keeps observing categories independently for badges and 1..9 filing.
 */
class LibraryRailViewModel(
    private val root: RootFolder,
    private val categories: CategoriesRepository,
    parentJob: Job? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Swing,
) : StateHolder(parentJob, dispatcher) {

    /** Category + member count, in display order (Favourites first), for the rail's rows. */
    val entries: StateFlow<List<Pair<Category, Int>>> =
        combine(
            categories.observeCategories(root),
            categories.observeMemberships(root),
        ) { cats, members -> cats.map { it to (members[it.id]?.size ?: 0) } }
            .stateIn(scope, SharingStarted.Eagerly, currentEntries())

    // Seed the StateFlow synchronously so the rail's first frame already has the categories the
    // repository holds (both upstreams are StateFlows with a current value), rather than flashing an
    // empty rail for a frame before the combine emits.
    private fun currentEntries(): List<Pair<Category, Int>> {
        val members = categories.observeMemberships(root).value
        return categories.observeCategories(root).value.map { it to (members[it.id]?.size ?: 0) }
    }

    fun create(name: String) {
        if (name.isBlank()) return
        scope.launch { categories.create(root, name) }
    }

    fun rename(id: CategoryId, newName: String) {
        if (newName.isBlank()) return
        scope.launch { categories.rename(root, id, newName) }
    }

    fun delete(id: CategoryId) {
        scope.launch { categories.delete(root, id) }
    }
}
