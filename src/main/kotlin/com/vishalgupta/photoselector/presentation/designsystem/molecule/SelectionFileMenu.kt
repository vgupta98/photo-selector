package com.vishalgupta.photoselector.presentation.designsystem.molecule

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vishalgupta.photoselector.domain.model.Category
import com.vishalgupta.photoselector.presentation.designsystem.atom.AppOutlinedButton

/**
 * The selection bar's filing menu: "Add to category" over the *custom* categories, in slot
 * order, reporting the chosen slot via [onSelectSlot] (slot `i` == the i-th custom category ==
 * the `i+1` digit key). Favourites rides its own first-class star button in the bar, mirroring
 * how [CategoryMenu] splits them in the All-Photos top bar. The caller only shows this when at
 * least one custom category exists. Owns its own menu visibility — local UI state.
 */
@Composable
fun SelectionFileMenu(
    customCategories: List<Category>,
    onSelectSlot: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        AppOutlinedButton(text = "Add to category", onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            customCategories.forEachIndexed { slot, category ->
                val prefix = if (slot < 9) "${slot + 1}  " else ""
                DropdownMenuItem(
                    text = { Text("$prefix${category.name}") },
                    onClick = {
                        expanded = false
                        onSelectSlot(slot)
                    },
                )
            }
        }
    }
}
