package com.vishalgupta.photoselector.presentation.designsystem.molecule

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * A plain two-button confirmation dialog for a guarded action — a deliberate speed bump in
 * front of something disruptive (e.g. leaving the current shoot to pick a new folder, or
 * moving photos to the Trash). The caller owns visibility; this only renders when mounted.
 * Keep [message] honest about the actual cost: most guarded actions here lose context, not
 * saved data.
 *
 * Set [confirmDestructive] when the confirm button performs an irreversible-ish, data-losing
 * action (delete); it tints the confirm action with the error colour so the danger reads at a
 * glance, while Cancel stays the visually quiet default.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
    confirmDestructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (confirmDestructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}
