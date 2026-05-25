package com.vishalgupta.photoselector.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Wraps [content] and exposes `controlsVisible` that becomes true on mouse-move and
 * fades to false after [idleTimeoutMs] of cursor inactivity within the bounds.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HoverOverlay(
    modifier: Modifier = Modifier,
    idleTimeoutMs: Long = 1500L,
    content: @Composable (controlsVisible: Boolean) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    var lastMove by remember { mutableLongStateOf(0L) }

    LaunchedEffect(idleTimeoutMs) {
        snapshotFlow { lastMove }
            .collectLatest {
                delay(idleTimeoutMs)
                visible = false
            }
    }

    Box(
        modifier
            .fillMaxSize()
            .onPointerEvent(PointerEventType.Move) {
                visible = true
                lastMove = System.currentTimeMillis()
            }
            .onPointerEvent(PointerEventType.Enter) {
                visible = true
                lastMove = System.currentTimeMillis()
            }
            .onPointerEvent(PointerEventType.Exit) { visible = false },
    ) {
        content(visible)
    }
}
