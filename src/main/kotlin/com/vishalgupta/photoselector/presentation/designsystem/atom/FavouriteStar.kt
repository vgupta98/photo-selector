package com.vishalgupta.photoselector.presentation.designsystem.atom

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * The favourite indicator: a filled star when [filled], an outline otherwise.
 * Size is controlled by the caller via [modifier]; [tint] defaults to the
 * ambient content color so it adapts inside buttons and toasts.
 */
@Composable
fun FavouriteStar(
    filled: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) {
    Icon(
        imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarOutline,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier,
    )
}
