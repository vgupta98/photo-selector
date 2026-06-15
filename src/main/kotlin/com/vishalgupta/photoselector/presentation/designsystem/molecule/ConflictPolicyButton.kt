package com.vishalgupta.photoselector.presentation.designsystem.molecule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vishalgupta.photoselector.domain.repository.ConflictPolicy
import com.vishalgupta.photoselector.presentation.designsystem.atom.AppButton
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme

// Plain-language collision choices. The labels describe the *outcome* for a name that
// already exists in the destination, not the internal [ConflictPolicy] verb: RENAME keeps
// both copies, SKIP leaves the existing file, OVERWRITE replaces it.
private val POLICY_OPTIONS = listOf(
    "Keep both" to ConflictPolicy.RENAME,
    "Skip duplicates" to ConflictPolicy.SKIP,
    "Replace existing" to ConflictPolicy.OVERWRITE,
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
            trailingIcon = Icons.Filled.ArrowDropDown,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // A header frames the three options as answers to one question, so each label
            // ("Keep both", etc.) reads in context instead of as a bare, ambiguous verb.
            Text(
                text = "If a file already exists in the folder:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AppTheme.spacing.lg, vertical = AppTheme.spacing.sm),
            )
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
