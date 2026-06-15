package com.vishalgupta.photoselector.perf

import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.domain.model.PhotoGroup
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.presentation.grid.buildRenderItems
import com.vishalgupta.photoselector.presentation.grid.displayGroupsFor
import com.vishalgupta.photoselector.presentation.grid.tileIndexForFlat
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Measures the grid's pure index/structure transforms over a large library - the work that runs on
 * every lens regroup and burst expand/collapse, off the Compose runtime. These are the functions the
 * whole grid index discipline rests on (`GridDisplayModel.kt` + `tileIndexForFlat`), and they regress
 * silently: no screenshot or recomposition test would notice them getting an order slower, so they
 * earn a JMH guard that the release-perf diff can catch.
 *
 * Fixture: [LIBRARY_SIZE] synthetic photos in two shapes - a flat grid (every photo its own tile, the
 * Off lens) and a heavily-bursted grid (runs of [BURST_LEN] collapsed into one tile each, the Time /
 * Similarity lenses). One burst in the middle is "expanded" so the explode path (header/footer +
 * per-frame singles) is exercised, not just the collapsed pass.
 *
 * No Compose, no IO: all work is in-memory list building, so the numbers are stable and reflect only
 * the transform cost. The flat case is the upper bound (one render item per photo); the bursted case
 * is the realistic large-shoot shape.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2)
@State(Scope.Benchmark)
open class GridDisplayModelBenchmark {

    private lateinit var flatGroups: List<PhotoGroup>
    private lateinit var burstedGroups: List<PhotoGroup>
    private lateinit var burstedTileFlatStart: List<Int>
    // PhotoId is a value class, so it can't be lateinit; seeded in setup() before any benchmark runs.
    private var expandedBurstId: PhotoId = PhotoId("")

    @Setup(Level.Trial)
    fun setup() {
        val photos = (0 until LIBRARY_SIZE).map { i ->
            Photo(
                id = PhotoId("p$i"),
                absolutePath = Path.of("/photos/p$i.jpg"),
                relativePath = "p$i.jpg",
                fileName = "p$i.jpg",
                sizeBytes = 4_823_901,
                lastModifiedEpochMs = 1_730_812_401_000 + i,
            )
        }

        flatGroups = photos.map(PhotoGroup::Single)

        // Collapse every contiguous run of BURST_LEN frames into one Burst tile - the heavily-grouped
        // shape a rapid-fire shoot produces under the Time / Similarity lenses.
        burstedGroups = photos.chunked(BURST_LEN).map { run ->
            if (run.size >= 2) PhotoGroup.Burst(run) else PhotoGroup.Single(run.first())
        }
        // Prefix sums: each tile's first flat photo index, ascending - exactly what the grid feeds
        // tileIndexForFlat as the flat<->tile bridge.
        burstedTileFlatStart = buildList(burstedGroups.size) {
            var acc = 0
            for (group in burstedGroups) {
                add(acc)
                acc += group.photos.size
            }
        }
        // A burst near the middle, so the explode path walks a realistic distance before reshaping.
        expandedBurstId = burstedGroups[burstedGroups.size / 2].groupId
    }

    /** Upper bound: one render item per photo, no grouping (the Off lens over the whole library). */
    @Benchmark
    fun buildRenderItemsFlat(): Int = buildRenderItems(flatGroups, expandedBurstId = null).size

    /** Realistic large-shoot shape: collapsed bursts, nothing expanded. */
    @Benchmark
    fun buildRenderItemsBursted(): Int = buildRenderItems(burstedGroups, expandedBurstId = null).size

    /** The explode path: one burst open, so its frames become header + per-frame tiles + footer. */
    @Benchmark
    fun buildRenderItemsBurstedExpanded(): Int = buildRenderItems(burstedGroups, expandedBurstId).size

    /** The display-group flatMap that the explode rides on (view-model side of the same reshape). */
    @Benchmark
    fun displayGroupsForExpanded(): Int = displayGroupsFor(burstedGroups, expandedBurstId).size

    /**
     * The flat->tile translation a scroll/anchor does per reshape: a binary search over the bursted
     * tile starts. Reported in nanoseconds - it is a single log-n search, dwarfed by the builders
     * above, but included so a regression (e.g. a linear scan creeping back in) shows up.
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    fun tileIndexForFlatLookup(): Int =
        // A flat index landing inside a burst run (not on a tile boundary) - the insertion-point arm.
        tileIndexForFlat(burstedTileFlatStart, flatIndex = LIBRARY_SIZE - BURST_LEN / 2)

    private companion object {
        const val LIBRARY_SIZE = 30_000
        const val BURST_LEN = 8
    }
}
