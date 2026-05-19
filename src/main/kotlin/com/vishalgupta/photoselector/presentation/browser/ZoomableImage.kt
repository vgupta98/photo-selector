package com.vishalgupta.photoselector.presentation.browser

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged

/**
 * Renders [bitmap] with user-controlled zoom and pan.
 *
 * Gestures:
 *  - Mouse/trackpad scroll (incl. macOS pinch, which the JBR delivers as scroll deltas) zooms.
 *  - Drag with the primary pointer pans, but only when zoomed in.
 *  - Double-click resets the transform.
 *
 * The transform resets whenever [bitmap] changes, so the next photo always starts at fit-to-screen.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ZoomableImage(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val zoom = rememberZoomState()

    LaunchedEffect(bitmap) { zoom.reset() }

    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged(zoom::setContainerSize)
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val deltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (deltaY != 0f) zoom.zoomByScroll(deltaY)
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { zoom.reset() })
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    if (zoom.scale > 1f) {
                        change.consume()
                        zoom.panBy(dragAmount)
                    }
                }
            },
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.High,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.offset.x
                    translationY = zoom.offset.y
                },
        )
    }
}
