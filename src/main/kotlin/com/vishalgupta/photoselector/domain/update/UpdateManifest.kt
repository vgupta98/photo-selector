package com.vishalgupta.photoselector.domain.update

/**
 * The server-controlled update feed, parsed and validated. The app reads (never writes) this from a
 * static file the developer hosts, so it is the entire control plane: editing the hosted file is how a
 * release is advertised, staged, floored, or pulled — no new app build required.
 *
 * - [latest] / [downloadUrl] / [notesUrl]: the build on offer and where to get it.
 * - [minimumVersion]: a hard floor — anyone below it is force-offered the update (see [mandatory]).
 * - [minOs]: don't offer to a machine too old to run it.
 * - [rollout]: fraction of installs (0.0..1.0) eligible for a *staged* rollout; an install's place in
 *   the wave is decided locally from its random id, so raising this widens the wave and 0.0 pulls it.
 * - [mandatory]: the user can't dismiss-and-forget (skip) this one.
 */
data class UpdateManifest(
    val latest: AppVersion,
    val downloadUrl: String,
    val notesUrl: String?,
    val minimumVersion: AppVersion?,
    val minOs: AppVersion?,
    val rollout: Double,
    val mandatory: Boolean,
)
