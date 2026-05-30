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
import com.vishalgupta.photoselector.domain.repository.ConflictPolicy
import com.vishalgupta.photoselector.presentation.designsystem.atom.AppButton

private val POLICY_OPTIONS = listOf(
    "If exists: Rename" to ConflictPolicy.RENAME,
    "If exists: Skip" to ConflictPolicy.SKIP,
    "If exists: Overwrite" to ConflictPolicy.OVERWRITE,
)

/**
 * "Copy photos to folder…" button that opens a menu of [ConflictPolicy] choices
 * and reports the picked policy via [onSelect]. Owns its own menu visibility —
 * purely local UI state with no reason to hoist.
 */
@Composable
fun ConflictPolicyButton(
    enabled: Boolean,
    onSelect: (ConflictPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        AppButton(
            text = "Copy photos to folder…",
            enabled = enabled,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            POLICY_OPTIONS.forEach { (label, policy) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onSelect(policy)
                    },
                )
            }
        }
    }
}
