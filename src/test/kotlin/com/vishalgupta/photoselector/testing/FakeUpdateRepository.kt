package com.vishalgupta.photoselector.testing

import com.vishalgupta.photoselector.domain.update.UpdateManifest
import com.vishalgupta.photoselector.domain.update.UpdateRepository

/** In-memory [UpdateRepository] for tests: returns whatever [manifest] is set to (null = fetch failed). */
class FakeUpdateRepository(var manifest: UpdateManifest? = null) : UpdateRepository {
    override suspend fun fetchManifest(): UpdateManifest? = manifest
}
