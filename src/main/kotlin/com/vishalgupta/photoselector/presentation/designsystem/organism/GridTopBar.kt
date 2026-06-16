package com.vishalgupta.photoselector.presentation.designsystem.organism

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vishalgupta.photoselector.domain.model.Category
import com.vishalgupta.photoselector.domain.repository.ConflictPolicy
import com.vishalgupta.photoselector.presentation.common.GroupingMode
import com.vishalgupta.photoselector.presentation.designsystem.molecule.ExportMenu
import com.vishalgupta.photoselector.presentation.designsystem.molecule.GroupingModeToggle
import com.vishalgupta.photoselector.presentation.navigation.CategoryScope

/**
 * Top bar for the photo grid, slimmed to two jobs now that the [LibraryRail] owns navigation and
 * category management. It carries only: a far-left toggle that collapses/expands the rail, the
 * current scope's identity (name + a muted photo count), and — pushed to the right — the view and
 * export controls. The grouping-lens toggle applies in every scope; **Export** appears only for an
 * exportable scope (Favourites or a custom category, not All Photos), exactly as before.
 *
 * What used to live here and now lives in the rail: the back button, the Favourites button, the
 * "Categories" dropdown, and the per-category rename/delete menu. The two former export controls
 * ("Export list (.txt)" + "Copy photos to folder…") are consolidated into one [ExportMenu].
 */
@Composable
fun GridTopBar(
    scope: CategoryScope,
    currentCategory: Category?,
    photoCount: Int,
    isBusy: Boolean,
    railCollapsed: Boolean,
    onToggleRail: () -> Unit,
    onExportTxt: () -> Unit,
    onCopyToFolder: (ConflictPolicy) -> Unit,
    groupingMode: GroupingMode,
    onSelectGroupingMode: (GroupingMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasPhotos = photoCount > 0 && !isBusy

    TopBarScaffold(modifier) {
        IconButton(onClick = onToggleRail) {
            Icon(
                imageVector = if (railCollapsed) Icons.Filled.Menu else Icons.AutoMirrored.Filled.MenuOpen,
                contentDescription = if (railCollapsed) "Show sidebar" else "Hide sidebar",
            )
        }

        val scopeName = when (scope) {
            CategoryScope.AllPhotos -> "All Photos"
            is CategoryScope.Category -> currentCategory?.name ?: "Category"
        }
        // Identity leads; the count is reference info, so it rides alongside as a muted caption.
        Text(scopeName, style = MaterialTheme.typography.titleLarge)
        Text(
            text = "$photoCount photo${if (photoCount == 1) "" else "s"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.weight(1f))

        // Export only for an exportable scope (Favourites + custom categories); All Photos has no
        // export action, matching the prior bar.
        if (scope is CategoryScope.Category) {
            ExportMenu(
                enabled = hasPhotos,
                onExportTxt = onExportTxt,
                onCopyToFolder = onCopyToFolder,
            )
        }

        GroupingModeToggle(mode = groupingMode, onSelect = onSelectGroupingMode)
    }
}
