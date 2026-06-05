package com.vishalgupta.photoselector.presentation.designsystem.organism

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import com.vishalgupta.photoselector.presentation.browser.ZoomState
import com.vishalgupta.photoselector.presentation.browser.ZoomableImage
import com.vishalgupta.photoselector.presentation.compare.ComparePane
import com.vishalgupta.photoselector.presentation.designsystem.atom.LoadingIndicator
import com.vishalgupta.photoselector.presentation.designsystem.molecule.ComparePaneHeader
import com.vishalgupta.photoselector.presentation.designsystem.molecule.ErrorPlaceholder
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme

/**
 * One side of the side-by-side compare view: the photo (zoomed via the caller-owned, shared
 * [zoom] so both panes move together), a [ComparePaneHeader] floating at the top, and a focus-ring
 * border when [isActive] so it's obvious which pane the keyboard is filing into. Pressing anywhere
 * in the pane makes it active ([onActivate]) without stealing the zoom/pan gestures underneath.
 *
 * The caller sizes the pane (e.g. `Modifier.weight(1f).fillMaxHeight()`); the active/inactive
 * border is always laid out at the same width so switching panes never nudges the layout.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ComparePaneView(
    pane: ComparePane,
    isActive: Boolean,
    totalInScope: Int,
    zoom: ZoomState,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .background(Color.Black)
            .border(
                width = AppTheme.dimens.focusBorderWidth,
                color = if (isActive) AppTheme.colors.focusRing else Color.Transparent,
            )
            .onPointerEvent(PointerEventType.Press) { onActivate() },
        contentAlignment = Alignment.Center,
    ) {
        when {
            pane.isLoading && pane.bitmap == null -> LoadingIndicator()
            pane.bitmap == null -> ErrorPlaceholder("Cannot decode this photo.")
            else -> ZoomableImage(
                bitmap = pane.bitmap,
                contentDescription = pane.photo?.fileName,
                zoom = zoom,
                modifier = Modifier.fillMaxSize(),
            )
        }

        val photo = pane.photo
        if (photo != null) {
            ComparePaneHeader(
                fileName = photo.fileName,
                positionLabel = "${pane.index + 1} / $totalInScope",
                isFavourite = pane.isFavourite,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(AppTheme.spacing.sm),
            )
        }
    }
}
