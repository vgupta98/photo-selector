package com.vishalgupta.photoselector.domain.update

import com.vishalgupta.photoselector.testing.FakeUpdateRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CheckForUpdateUseCaseTest {

    private val current = AppVersion(1, 6, 0)
    private val os = AppVersion(14, 5, 0)

    private fun manifest(
        latest: String = "1.7.0",
        rollout: Double = 1.0,
        mandatory: Boolean = false,
        minimumVersion: String? = null,
        minOs: String? = null,
    ) = UpdateManifest(
        latest = AppVersion.parseOrNull(latest)!!,
        downloadUrl = "https://example.com/Rhenium-$latest.dmg",
        notesUrl = "https://example.com/notes",
        minimumVersion = minimumVersion?.let(AppVersion::parseOrNull),
        minOs = minOs?.let(AppVersion::parseOrNull),
        rollout = rollout,
        mandatory = mandatory,
    )

    private suspend fun check(m: UpdateManifest?, bucket: Double = 0.5): UpdateStatus =
        CheckForUpdateUseCase(FakeUpdateRepository(m)).invoke(current, os, bucket)

    @Test fun `a missing feed is Unknown, never a crash`() = runTest {
        assertIs<UpdateStatus.Unknown>(check(null))
    }

    @Test fun `same or older latest is up to date`() = runTest {
        assertIs<UpdateStatus.UpToDate>(check(manifest(latest = "1.6.0")))
        assertIs<UpdateStatus.UpToDate>(check(manifest(latest = "1.5.9")))
    }

    @Test fun `a newer build in a full rollout is offered`() = runTest {
        val status = assertIs<UpdateStatus.Available>(check(manifest(latest = "1.7.0")))
        assertEquals(AppVersion(1, 7, 0), status.version)
        assertEquals(false, status.mandatory)
    }

    @Test fun `an install outside the rollout wave is held back`() = runTest {
        // bucket 0.5 is above the 0.1 cut, so this install isn't in the wave yet.
        assertIs<UpdateStatus.UpToDate>(check(manifest(rollout = 0.1), bucket = 0.5))
    }

    @Test fun `an install inside the rollout wave is offered`() = runTest {
        assertIs<UpdateStatus.Available>(check(manifest(rollout = 0.1), bucket = 0.05))
    }

    @Test fun `the mandatory floor overrides the rollout and forces the update`() = runTest {
        // Held out of the wave (rollout 0), but below the minimum floor — must update anyway.
        val status = assertIs<UpdateStatus.Available>(
            check(manifest(minimumVersion = "1.6.5", rollout = 0.0), bucket = 0.99),
        )
        assertEquals(true, status.mandatory)
    }

    @Test fun `a manifest-level mandatory flag is carried through`() = runTest {
        val status = assertIs<UpdateStatus.Available>(check(manifest(mandatory = true)))
        assertEquals(true, status.mandatory)
    }

    @Test fun `a build needing a newer OS is not offered`() = runTest {
        assertIs<UpdateStatus.UpToDate>(check(manifest(minOs = "15.0")))
    }

    @Test fun `the OS floor passing still offers the update`() = runTest {
        assertIs<UpdateStatus.Available>(check(manifest(minOs = "13.0")))
    }

    @Test fun `an unknown OS version fails open and still offers the update`() = runTest {
        // os.version unparseable -> osVersion null. Notify-only errs toward informing the user
        // rather than hiding the update, so the OS floor is skipped when the OS can't be read.
        val status = CheckForUpdateUseCase(FakeUpdateRepository(manifest(minOs = "15.0")))
            .invoke(current, osVersion = null, rolloutBucket = 0.5)
        assertIs<UpdateStatus.Available>(status)
    }
}
