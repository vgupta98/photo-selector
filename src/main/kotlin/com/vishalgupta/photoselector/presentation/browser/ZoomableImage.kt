package com.vishalgupta.photoselector.presentation.browser

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged

/**
 * Renders [bitmap] with the transform held in [zoom].
 *
 * Gestures:
 *  - Mouse / trackpad two-finger scroll (and pinch where the JDK delivers it as scroll) zooms.
 *  - Drag with the primary pointer pans, but only when zoomed in.
 *  - Double-click resets the transform.
 *
 * The caller owns [zoom] so it can also drive zoom from keyboard shortcuts and reset on photo
 * change without coupling that logic to the image surface.
 */
@Composable
fun ZoomableImage(
    bitmap: ImageBitmap,
    contentDescription: String?,
    zoom: ZoomState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged(zoom::setContainerSize)
            .pointerInput(zoom) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Scroll) continue
                        val change = event.changes.firstOrNull() ?: continue
                        val dy = change.scrollDelta.y
                        if (dy != 0f) {
                            zoom.zoomByScroll(dy)
                            change.consume()
                        }
                    }
                }
            }
            .pointerInput(zoom) {
                detectTapGestures(onDoubleTap = { zoom.reset() })
            }
            .pointerInput(zoom) {
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
                // Keep the lambda form. Reading zoom.scale/offset inside this block
                // defers the read to the draw phase, so a per-frame scroll-zoom or
                // pan gesture updates the layer WITHOUT recomposing ZoomableImage.
                // The value form — graphicsLayer(scaleX = zoom.scale, ...) — looks
                // identical and renders identically, but moves the read into
                // composition and recomposes this composable every frame of the
                // gesture. Do not "simplify" it. (This can't be caught by a test:
                // the recomposition is confined to this scope and unobservable from
                // outside; see CLAUDE.md "Checking for unnecessary recompositions".)
                .graphicsLayer {
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.offset.x
                    translationY = zoom.offset.y
                },
        )
    }
}
