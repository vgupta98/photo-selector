package com.vishalgupta.photoselector.presentation.designsystem.organism

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.vishalgupta.photoselector.domain.model.Category
import com.vishalgupta.photoselector.domain.model.CategoryId
import com.vishalgupta.photoselector.presentation.designsystem.molecule.CategoryHudChip
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme
import com.vishalgupta.photoselector.presentation.designsystem.theme.PillShape

/**
 * The heads-up legend overlaid on the full-screen browser: one [CategoryHudChip] per
 * category showing its hotkey and whether the current photo (whose memberships are
 * [currentMemberships]) belongs to it. Tapping a chip toggles membership — the same effect
 * as the keyboard. Favourites comes first with the `F` key and the gold star; custom
 * categories follow with bare digits `1`..`9` (the 10th-plus get no digit).
 *
 * Visibility (auto-hide / reveal-on-interaction) is the caller's concern — this just draws
 * the strip.
 */
@Composable
fun BrowserCategoryHud(
    categories: List<Category>,
    currentMemberships: Set<CategoryId>,
    onToggle: (CategoryId) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (categories.isEmpty()) return
    val favourites = categories.firstOrNull { it.id == Category.FAVOURITES_ID }
    val customs = categories.filter { !it.builtIn }
    val inactiveContainer = Color.White.copy(alpha = 0.16f)

    Surface(
        shape = PillShape,
        color = Color.Black.copy(alpha = 0.6f),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.spacing.md, vertical = AppTheme.spacing.sm),
        ) {
            if (favourites != null) {
                val active = Category.FAVOURITES_ID in currentMemberships
                CategoryHudChip(
                    keyLabel = "F",
                    label = favourites.name,
                    active = active,
                    star = true,
                    containerColor = if (active) AppTheme.colors.favourite else inactiveContainer,
                    contentColor = if (active) AppTheme.colors.favouriteToastContent else Color.White,
                    onClick = { onToggle(Category.FAVOURITES_ID) },
                )
            }
            customs.forEachIndexed { slot, category ->
                val active = category.id in currentMemberships
                CategoryHudChip(
                    keyLabel = if (slot < 9) "${slot + 1}" else null,
                    label = category.name,
                    active = active,
                    containerColor = if (active) AppTheme.colors.categoryMemberContainer else inactiveContainer,
                    contentColor = if (active) AppTheme.colors.categoryMemberContent else Color.White,
                    onClick = { onToggle(category.id) },
                )
            }
        }
    }
}
