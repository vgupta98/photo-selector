package com.vishalgupta.photoselector.presentation.designsystem.organism

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vishalgupta.photoselector.domain.model.Category
import com.vishalgupta.photoselector.domain.model.CategoryId
import com.vishalgupta.photoselector.domain.repository.ConflictPolicy
import com.vishalgupta.photoselector.presentation.designsystem.atom.AppTextButton
import com.vishalgupta.photoselector.presentation.designsystem.molecule.BurstGroupingToggle
import com.vishalgupta.photoselector.presentation.designsystem.molecule.CategoryActionsMenu
import com.vishalgupta.photoselector.presentation.designsystem.molecule.CategoryMenu
import com.vishalgupta.photoselector.presentation.designsystem.molecule.CategoryNameDialog
import com.vishalgupta.photoselector.presentation.designsystem.molecule.ChangeFolderButton
import com.vishalgupta.photoselector.presentation.designsystem.molecule.ConflictPolicyButton
import com.vishalgupta.photoselector.presentation.designsystem.molecule.FavouritesButton
import com.vishalgupta.photoselector.presentation.navigation.CategoryScope

/**
 * Top bar for the photo grid. In [CategoryScope.AllPhotos] it shows a photo count, a
 * first-class **Favourites** button (the keeper bucket, promoted out of the dropdown to
 * match the browser bar), and the custom-categories dropdown; in a [CategoryScope.Category]
 * it adds a back button, the export / copy actions, and — for non-built-in categories — a
 * "⋯" Rename / Delete menu. "Change folder" is guarded by a confirm dialog because it tears
 * the current session down and re-scans.
 */
@Composable
fun GridTopBar(
    scope: CategoryScope,
    currentCategory: Category?,
    photoCount: Int,
    categoryEntries: List<Pair<Category, Int>>,
    isBusy: Boolean,
    onBack: (() -> Unit)?,
    onSelectCategory: (CategoryId) -> Unit,
    onCreateCategory: (String) -> Unit,
    onRenameCategory: (CategoryId, String) -> Unit,
    onDeleteCategory: (CategoryId) -> Unit,
    onExportTxt: () -> Unit,
    onCopyToFolder: (ConflictPolicy) -> Unit,
    onChangeFolder: () -> Unit,
    groupBursts: Boolean,
    onToggleGroupBursts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    val hasPhotos = photoCount > 0 && !isBusy

    TopBarScaffold(modifier) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
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

        if (scope == CategoryScope.AllPhotos) {
            FavouritesButton(
                count = categoryEntries.firstOrNull { it.first.builtIn }?.second ?: 0,
                onClick = { onSelectCategory(Category.FAVOURITES_ID) },
            )
            CategoryMenu(
                entries = categoryEntries,
                onSelect = onSelectCategory,
                onCreateRequested = { showCreateDialog = true },
            )
        }

        Spacer(Modifier.weight(1f))

        if (scope is CategoryScope.Category) {
            AppTextButton(
                text = "Export list (.txt)",
                enabled = hasPhotos,
                onClick = onExportTxt,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ConflictPolicyButton(enabled = hasPhotos, onSelect = onCopyToFolder)
            val current = currentCategory
            if (current != null && !current.builtIn) {
                CategoryActionsMenu(
                    categoryName = current.name,
                    onRenameRequested = { showRenameDialog = true },
                    onDeleteConfirmed = { onDeleteCategory(current.id) },
                )
            }
        }

        BurstGroupingToggle(grouping = groupBursts, onToggle = onToggleGroupBursts)

        ChangeFolderButton(
            onChangeFolder = onChangeFolder,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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

    if (showRenameDialog && currentCategory != null) {
        CategoryNameDialog(
            title = "Rename category",
            confirmLabel = "Rename",
            initialName = currentCategory.name,
            onConfirm = {
                showRenameDialog = false
                onRenameCategory(currentCategory.id, it)
            },
            onDismiss = { showRenameDialog = false },
        )
    }
}
