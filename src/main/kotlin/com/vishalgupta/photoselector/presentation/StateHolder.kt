package com.vishalgupta.photoselector.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.Dispatchers

abstract class StateHolder {
    private val job = SupervisorJob()
    protected val scope: CoroutineScope = CoroutineScope(Dispatchers.Swing + job)

    open fun onClear() {
        job.cancel()
    }
}
