package com.vishalgupta.photoselector.perf

import com.vishalgupta.photoselector.data.favourites.JsonFavouritesRepository
import com.vishalgupta.photoselector.domain.model.PhotoId
import com.vishalgupta.photoselector.domain.model.RootFolder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.annotations.Warmup
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.TimeUnit

/**
 * Measures end-to-end latency of `JsonFavouritesRepository.toggle`. Each
 * invocation flips a single id and waits for the suspending toggle to return.
 *
 * Same id is flipped every invocation so the set alternately gains and loses
 * one entry — every toggle dirties the JSON and exercises the disk write path.
 * `develop` schedules a debounced write (toggle returns immediately, ~µs);
 * `chore/repo-scope-ownership` performs the atomic-rename write inline (toggle
 * returns ~hundreds of µs to low ms depending on the filesystem). The delta is
 * the cost we're trading "no silent loss on quit" for.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2)
@State(Scope.Benchmark)
open class FavouritesToggleBenchmark {

    private lateinit var tmpDir: Path
    private lateinit var root: RootFolder
    private lateinit var repo: JsonFavouritesRepository
    private val id = PhotoId("bench-photo")

    @Setup(Level.Trial)
    fun setup() {
        tmpDir = Files.createTempDirectory("favbench-")
        root = RootFolder(tmpDir)
        repo = JsonFavouritesRepository(Json { prettyPrint = true; encodeDefaults = true })
        // Force initial bind so the first measured toggle isn't disproportionately slow.
        repo.observe(root)
    }

    @TearDown(Level.Trial)
    fun teardown() {
        if (Files.exists(tmpDir)) {
            Files.walk(tmpDir).use { stream ->
                stream.sorted(Comparator.reverseOrder())
                    .forEach { Files.deleteIfExists(it) }
            }
        }
    }

    @Benchmark
    fun toggleSameId() = runBlocking {
        repo.toggle(root, id)
    }
}
