package com.vishalgupta.photoselector.presentation.designsystem.molecule

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vishalgupta.photoselector.presentation.designsystem.atom.AppOutlinedButton

/**
 * The "Favourites (n)" action, shared by the grid and browser top bars so the
 * affordance looks identical wherever the user is.
 */
@Composable
fun FavouritesButton(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    AppOutlinedButton(
        text = "Favourites ($count)",
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = Icons.Filled.Star,
    )
}
