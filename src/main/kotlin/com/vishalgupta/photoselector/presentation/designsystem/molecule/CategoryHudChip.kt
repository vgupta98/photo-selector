package com.vishalgupta.photoselector.presentation.designsystem.molecule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.vishalgupta.photoselector.presentation.designsystem.atom.FavouriteStar
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme
import com.vishalgupta.photoselector.presentation.designsystem.theme.PillShape

/**
 * One toggle in the browser's category HUD: a hotkey cap ([keyLabel], e.g. "F" or "3"), an
 * optional [star] for the Favourites chip, and the category [label]. Lit when the current
 * photo is a member; clicking toggles it. Colours are passed in by the [CategoryHudChip]'s
 * organism so the active accent (gold for Favourites, primary for custom) stays one decision.
 */
@Composable
fun CategoryHudChip(
    keyLabel: String?,
    label: String,
    active: Boolean,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    star: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = PillShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
            modifier = Modifier.padding(horizontal = AppTheme.spacing.md, vertical = AppTheme.spacing.sm),
        ) {
            if (keyLabel != null) {
                Text(
                    keyLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (star) {
                FavouriteStar(filled = active, modifier = Modifier.size(AppTheme.dimens.iconSm))
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
