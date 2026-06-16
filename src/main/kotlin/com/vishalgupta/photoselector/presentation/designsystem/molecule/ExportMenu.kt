package com.vishalgupta.photoselector.presentation.designsystem.molecule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vishalgupta.photoselector.domain.repository.ConflictPolicy
import com.vishalgupta.photoselector.presentation.designsystem.atom.AppOutlinedButton
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme

/**
 * The grid top bar's single **Export** entry point, consolidating the two output paths that used to
 * sit as separate top-bar controls — "Export list (.txt)" and "Copy photos to folder…" — into one
 * dropdown so the bar carries one export affordance rather than two competing ones.
 *
 * The menu lists the text export, then the copy-to-folder choices grouped under the same
 * [ConflictPolicyHeader] / [ConflictPolicyOptions] the standalone [ConflictPolicyButton] uses, so a
 * collision choice reads identically wherever it appears. This menu is also the intended home for a
 * future plugin exporter: an extra contributed format appends as one more item after a divider,
 * with no change to the surrounding bar. Owns its own visibility — purely local UI state.
 */
@Composable
fun ExportMenu(
    enabled: Boolean,
    onExportTxt: () -> Unit,
    onCopyToFolder: (ConflictPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        AppOutlinedButton(
            text = "Export",
            enabled = enabled,
            onClick = { expanded = true },
            trailingIcon = Icons.Filled.ArrowDropDown,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Export list (.txt)") },
                onClick = {
                    expanded = false
                    onExportTxt()
                },
            )
            HorizontalDivider()
            // Copy-to-folder, with the collision choice inlined as the next level so the whole
            // export surface is one flat menu rather than a button-plus-popup.
            Text(
                text = "Copy to folder — $ConflictPolicyHeader",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AppTheme.spacing.lg, vertical = AppTheme.spacing.sm),
            )
            ConflictPolicyOptions.forEach { (label, policy) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onCopyToFolder(policy)
                    },
                )
            }
        }
    }
}
