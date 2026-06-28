package com.vishalgupta.photoselector.domain.update

/** Reads the hosted update feed. Returns null for *any* failure (offline, 404, malformed) — the check is best-effort and never blocks or crashes the app over a missed fetch. */
interface UpdateRepository {
    suspend fun fetchManifest(): UpdateManifest?
}
