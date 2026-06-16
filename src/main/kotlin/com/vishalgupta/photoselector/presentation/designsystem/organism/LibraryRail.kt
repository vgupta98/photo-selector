package com.vishalgupta.photoselector.presentation.designsystem.organism

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vishalgupta.photoselector.domain.model.Category
import com.vishalgupta.photoselector.domain.model.CategoryId
import com.vishalgupta.photoselector.presentation.common.categorySlotDigit
import com.vishalgupta.photoselector.presentation.designsystem.atom.FavouriteStar
import com.vishalgupta.photoselector.presentation.designsystem.molecule.CategoryActionsMenu
import com.vishalgupta.photoselector.presentation.designsystem.molecule.CategoryNameDialog
import com.vishalgupta.photoselector.presentation.designsystem.molecule.ChangeFolderButton
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme
import com.vishalgupta.photoselector.presentation.navigation.CategoryScope

/**
 * The left navigation rail: the library's scopes laid out as a vertical list rather than crammed
 * into the top bar. It owns folder identity (the root name + "Change folder"), the All Photos and
 * Favourites scopes, every custom category with its membership count, and category management
 * (create / rename / delete). The active scope is highlighted; selecting a row navigates to that
 * scope's own grid.
 *
 * This consolidates affordances that used to be split across the top bar (Favourites button, the
 * "Categories" dropdown, the per-category "⋯" menu, "Change folder") into one stable, scannable
 * column — leaving the top bar to carry only the current scope's identity and the view/export
 * controls. It is composed entirely from existing pieces ([FavouriteStar], [CategoryActionsMenu],
 * [CategoryNameDialog], [ChangeFolderButton]) so the affordances look identical to before.
 *
 * Rows are mouse-clickable but deliberately **not** keyboard-focusable
 * ([Modifier.focusProperties] `canFocus = false`): the grid owns the keyboard ring, and a rail row
 * grabbing focus would silently break arrow-key navigation.
 */
@Composable
fun LibraryRail(
    rootName: String,
    scope: CategoryScope,
    // Category + member count, Favourites first then custom in slot order — the same list the grid
    // derives for its badges, so a rail count and a tile badge can never disagree.
    entries: List<Pair<Category, Int>>,
    onSelectAllPhotos: () -> Unit,
    onSelectCategory: (CategoryId) -> Unit,
    onCreateCategory: (String) -> Unit,
    onRenameCategory: (CategoryId, String) -> Unit,
    onDeleteCategory: (CategoryId) -> Unit,
    onChangeFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    // The custom category currently being renamed (the rail owns one shared dialog rather than one
    // per row), or null when no rename is in flight.
    var renaming by remember { mutableStateOf<Category?>(null) }

    val favourites = entries.firstOrNull { it.first.builtIn }
    val customEntries = entries.filter { !it.first.builtIn }

    Column(
        modifier
            .width(AppTheme.dimens.libraryRailWidth)
            .fillMaxHeight()
            .background(AppTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = AppTheme.spacing.sm),
    ) {
        RailHeader(rootName = rootName, onChangeFolder = onChangeFolder)

        Spacer(Modifier.height(AppTheme.spacing.sm))

        RailRow(
            label = "All Photos",
            selected = scope == CategoryScope.AllPhotos,
            onClick = onSelectAllPhotos,
            leading = {
                Icon(
                    Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(AppTheme.dimens.iconSm),
                )
            },
        )

        if (favourites != null) {
            RailRow(
                label = favourites.first.name,
                selected = scope.isCategory(favourites.first.id),
                count = favourites.second,
                onClick = { onSelectCategory(favourites.first.id) },
                leading = {
                    FavouriteStar(
                        filled = true,
                        tint = AppTheme.colors.favourite,
                        modifier = Modifier.size(AppTheme.dimens.iconSm),
                    )
                },
            )
        }

        RailSectionLabel("Categories")

        customEntries.forEachIndexed { slot, (category, count) ->
            RailRow(
                label = category.name,
                selected = scope.isCategory(category.id),
                count = count,
                onClick = { onSelectCategory(category.id) },
                leading = { RailSlotBadge(slot) },
                // Rename / delete ride a hover-revealed "⋯" so the resting rail stays clean; the
                // count yields to the menu while it's shown. Built-in Favourites never gets one.
                actions = {
                    CategoryActionsMenu(
                        categoryName = category.name,
                        onRenameRequested = { renaming = category },
                        onDeleteConfirmed = { onDeleteCategory(category.id) },
                    )
                },
            )
        }

        RailRow(
            label = "New category",
            selected = false,
            onClick = { showCreateDialog = true },
            contentColor = AppTheme.colorScheme.onSurfaceVariant,
            leading = {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(AppTheme.dimens.iconSm),
                )
            },
        )
    }

    if (showCreateDialog) {
        CategoryNameDialog(
            title = "New category",
            confirmLabel = "Create",
            onConfirm = {
                showCreateDialog = false
                onCreateCategory(it)
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    renaming?.let { category ->
        CategoryNameDialog(
            title = "Rename category",
            confirmLabel = "Rename",
            initialName = category.name,
            onConfirm = {
                renaming = null
                onRenameCategory(category.id, it)
            },
            onDismiss = { renaming = null },
        )
    }
}

/** Folder identity at the top of the rail: the open folder's name, with the "Change folder" action. */
@Composable
private fun RailHeader(rootName: String, onChangeFolder: () -> Unit) {
    Column(Modifier.padding(horizontal = AppTheme.spacing.md)) {
        Text(
            text = rootName,
            style = AppTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ChangeFolderButton(
            onChangeFolder = onChangeFolder,
            contentColor = AppTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Muted group label between rail sections (e.g. "Categories"). */
@Composable
private fun RailSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = AppTheme.typography.labelSmall,
        color = AppTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = AppTheme.spacing.md,
            top = AppTheme.spacing.md,
            bottom = AppTheme.spacing.xs,
        ),
    )
}

/** The "1".."9" slot hint for a custom category, echoing its bare-digit filing key. Blank past 9. */
@Composable
private fun RailSlotBadge(slot: Int) {
    Box(
        Modifier.size(AppTheme.dimens.iconSm),
        contentAlignment = Alignment.Center,
    ) {
        // Derived from the same helper the filing-key prefixes use, so the badge and the keys
        // can't disagree on the 1..9 cap.
        categorySlotDigit(slot)?.let { digit ->
            Text(
                text = digit,
                style = AppTheme.typography.labelMedium,
                color = AppTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One rail entry: a clickable, full-width row with a leading glyph, an ellipsised label, a member
 * [count], and an optional trailing [actions] slot (the "⋯" rename/delete menu, custom categories
 * only). The active scope's row reads with a neutral fill (state is hueless here, the warm accent
 * stays reserved for the favourite star and primary actions). The row is given a uniform minimum
 * height so a row carrying the taller "⋯" icon button doesn't stand proud of its neighbours. Not
 * keyboard-focusable, by design — see [LibraryRail].
 */
@Composable
private fun RailRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    count: Int? = null,
    actions: (@Composable () -> Unit)? = null,
    contentColor: Color = AppTheme.colorScheme.onSurface,
) {
    val fill = if (selected) {
        AppTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    } else {
        Color.Transparent
    }
    Row(
        Modifier
            .padding(horizontal = AppTheme.spacing.sm, vertical = 1.dp)
            .fillMaxWidth()
            .heightIn(min = RAIL_ROW_MIN_HEIGHT)
            .clip(AppTheme.shapes.small)
            .background(fill)
            // Announce the scope as a selectable tab so assistive tech reports the active row,
            // even though the row stays out of the keyboard focus order (below).
            .semantics {
                this.selected = selected
                role = Role.Tab
            }
            // The grid owns the keyboard ring; a focusable rail row would steal it and kill arrows.
            .focusProperties { canFocus = false }
            // The active row is inert: clicking the scope you're already in would push a redundant
            // navigation and recompute the cold scroll index, so only inactive rows are clickable.
            .clickable(enabled = !selected, onClick = onClick)
            .padding(start = AppTheme.spacing.sm, end = AppTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
    ) {
        leading()
        Text(
            text = label,
            style = AppTheme.typography.bodyMedium,
            color = if (selected) AppTheme.colorScheme.onSurface else contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count != null) {
            Text(
                text = "$count",
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colorScheme.onSurfaceVariant,
            )
        }
        actions?.invoke()
    }
}

/** Uniform minimum height for a rail row, so the "⋯"-bearing custom rows align with the rest. */
private val RAIL_ROW_MIN_HEIGHT = 40.dp

/** True when this scope is the given category — the rail's active-row test. */
private fun CategoryScope.isCategory(id: CategoryId): Boolean =
    this is CategoryScope.Category && this.id == id
