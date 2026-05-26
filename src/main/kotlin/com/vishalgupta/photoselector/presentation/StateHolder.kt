package com.vishalgupta.photoselector.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.swing.Swing

abstract class StateHolder(parent: Job? = null) {
    private val job = SupervisorJob(parent)
    protected val scope: CoroutineScope = CoroutineScope(Dispatchers.Swing + job)

    open fun onClear() {
        job.cancel()
    }
}
