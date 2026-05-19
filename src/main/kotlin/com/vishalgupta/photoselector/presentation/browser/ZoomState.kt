package com.vishalgupta.photoselector.presentation.browser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.exp

/**
 * Holds the user-driven zoom transform for a single image. Stateless about the underlying photo;
 * call [reset] when the photo changes.
 *
 * Scale is in the range [[MIN_SCALE], [MAX_SCALE]]. The translation is clamped so the scaled
 * content cannot be panned past its own edges within the container.
 */
class ZoomState {

    var scale: Float by mutableStateOf(1f)
        private set

    var offset: Offset by mutableStateOf(Offset.Zero)
        private set

    private var containerSize: IntSize = IntSize.Zero

    fun setContainerSize(size: IntSize) {
        containerSize = size
        offset = clamp(offset, scale)
    }

    /** Multiplicative zoom; positive [scrollDeltaY] zooms out, negative zooms in (matches macOS). */
    fun zoomByScroll(scrollDeltaY: Float) {
        applyScale(scale * exp(-scrollDeltaY * ZOOM_SENSITIVITY))
    }

    fun zoomIn() = applyScale(scale * KEY_STEP)
    fun zoomOut() = applyScale(scale / KEY_STEP)

    fun panBy(drag: Offset) {
        if (scale <= 1f) return
        offset = clamp(offset + drag, scale)
    }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    private fun applyScale(target: Float) {
        val newScale = target.coerceIn(MIN_SCALE, MAX_SCALE)
        scale = newScale
        offset = if (newScale <= 1f) Offset.Zero else clamp(offset, newScale)
    }

    private fun clamp(o: Offset, s: Float): Offset {
        if (containerSize == IntSize.Zero) return o
        val maxX = containerSize.width * (s - 1f) / 2f
        val maxY = containerSize.height * (s - 1f) / 2f
        return Offset(
            x = o.x.coerceIn(-maxX, maxX),
            y = o.y.coerceIn(-maxY, maxY),
        )
    }

    companion object {
        const val MIN_SCALE: Float = 1f
        const val MAX_SCALE: Float = 8f
        private const val ZOOM_SENSITIVITY: Float = 0.18f
        private const val KEY_STEP: Float = 1.25f
    }
}

@Composable
fun rememberZoomState(): ZoomState = remember { ZoomState() }
