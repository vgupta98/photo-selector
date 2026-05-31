package com.vishalgupta.photoselector.presentation.designsystem.molecule

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vishalgupta.photoselector.domain.model.Category
import com.vishalgupta.photoselector.domain.model.CategoryId
import com.vishalgupta.photoselector.presentation.designsystem.atom.AppOutlinedButton

/**
 * The All-Photos top-bar entry point into categories: a "Categories" button opening a
 * menu of every category with its member count, plus a "New category…" item. The leading
 * digit (1..9) hints the `Cmd+N` membership shortcut for that category. Owns its own menu
 * visibility — purely local UI state.
 */
@Composable
fun CategoryMenu(
    entries: List<Pair<Category, Int>>,
    onSelect: (CategoryId) -> Unit,
    onCreateRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        AppOutlinedButton(text = "Categories (${entries.size})", onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            entries.forEachIndexed { index, (category, count) ->
                val prefix = if (index < 9) "${index + 1}  " else ""
                DropdownMenuItem(
                    text = { Text("$prefix${category.name}  ($count)") },
                    onClick = {
                        expanded = false
                        onSelect(category.id)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("New category…") },
                onClick = {
                    expanded = false
                    onCreateRequested()
                },
            )
        }
    }
}
