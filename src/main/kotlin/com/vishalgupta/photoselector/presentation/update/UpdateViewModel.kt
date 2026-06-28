package com.vishalgupta.photoselector.presentation.update

import com.vishalgupta.photoselector.domain.update.AppVersion
import com.vishalgupta.photoselector.domain.update.CheckForUpdateUseCase
import com.vishalgupta.photoselector.domain.update.UpdateStatus
import com.vishalgupta.photoselector.presentation.StateHolder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing

/** What the update banner needs to render; null [available] means no banner. */
data class UpdateUiState(val available: Available? = null) {
    data class Available(
        val versionLabel: String,
        val downloadUrl: String,
        val notesUrl: String?,
        val mandatory: Boolean,
    )
}

/**
 * App-lifetime holder for the "an update is available" banner. Runs the check once on launch (off the
 * EDT, via the use case's IO fetch), applies the user's "skip this version" choice the pure use case
 * doesn't know about, and turns the user's banner actions into a browser open + a dismissal.
 *
 * Notify-only by design: the most it ever does is open the download page — it never installs anything.
 * The whole feature is inert when [autoCheckEnabled] is false (the user opted out, or a Homebrew-managed
 * install defers to `brew upgrade`), so [check] is a no-op and no request is ever made.
 */
class UpdateViewModel(
    private val checkForUpdate: CheckForUpdateUseCase,
    private val currentVersion: AppVersion,
    private val osVersion: AppVersion?,
    private val rolloutBucket: Double,
    private val autoCheckEnabled: Boolean,
    private val skippedVersion: () -> String?,
    private val onSkipVersion: (String) -> Unit,
    private val openUrl: (String) -> Unit,
    parentJob: Job? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Swing,
) : StateHolder(parentJob, dispatcher) {

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var checkJob: Job? = null

    /** Check the feed and surface the banner if an eligible, non-skipped update is offered. Idempotent. */
    fun check() {
        if (!autoCheckEnabled || checkJob?.isActive == true) return
        checkJob = scope.launch {
            val available = (checkForUpdate(currentVersion, osVersion, rolloutBucket) as? UpdateStatus.Available)
                // A skipped version stays hidden — unless it's mandatory, which can't be skipped.
                ?.takeUnless { !it.mandatory && it.version.toString() == skippedVersion() }
                ?.let {
                    UpdateUiState.Available(
                        versionLabel = it.version.toString(),
                        downloadUrl = it.downloadUrl,
                        notesUrl = it.notesUrl,
                        mandatory = it.mandatory,
                    )
                }
            _state.value = UpdateUiState(available = available)
        }
    }

    fun onDownload() {
        val available = _state.value.available ?: return
        openUrl(available.downloadUrl)
        // A mandatory update stays up until the user actually has the new build; an optional one steps aside.
        if (!available.mandatory) _state.value = UpdateUiState()
    }

    fun onViewNotes() {
        _state.value.available?.notesUrl?.let(openUrl)
    }

    /** Dismiss for this session only; the banner can return on the next launch. */
    fun onLater() {
        _state.value = UpdateUiState()
    }

    /** Don't offer this version again. No-op for a mandatory update. */
    fun onSkip() {
        val available = _state.value.available ?: return
        if (available.mandatory) return
        onSkipVersion(available.versionLabel)
        _state.value = UpdateUiState()
    }
}
