package com.vishalgupta.photoselector.presentation.designsystem.molecule

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * The "⋯" overflow for a custom category's top bar: Rename (defers to a dialog the caller
 * owns) and Delete (guarded by a local confirmation). Absent for the built-in Favourites,
 * which cannot be renamed or deleted — the caller simply doesn't place this menu there.
 */
@Composable
fun CategoryActionsMenu(
    categoryName: String,
    onRenameRequested: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Category actions")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Rename…") },
                onClick = {
                    expanded = false
                    onRenameRequested()
                },
            )
            DropdownMenuItem(
                text = { Text("Delete…") },
                onClick = {
                    expanded = false
                    confirmingDelete = true
                },
            )
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete category?") },
            text = { Text("\"$categoryName\" will be removed. The photos themselves are not deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    onDeleteConfirmed()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            },
        )
    }
}
