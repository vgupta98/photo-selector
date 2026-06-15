package com.vishalgupta.photoselector.presentation

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.swing.Swing

// `dispatcher` defaults to the Swing EDT (the only correct choice in the running app); tests
// inject a TestDispatcher to drive the scope's coroutines deterministically.
abstract class StateHolder(
    parent: Job? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Swing,
) {
    private val job = SupervisorJob(parent)
    protected val scope: CoroutineScope = CoroutineScope(dispatcher + job)

    open fun onClear() {
        job.cancel()
    }
}
