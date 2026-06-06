package com.vishalgupta.photoselector.presentation.designsystem.molecule

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * A plain two-button confirmation dialog for a guarded action — a deliberate speed bump in
 * front of something disruptive-but-not-destructive (e.g. leaving the current shoot to pick a
 * new folder). The caller owns visibility; this only renders when mounted. Keep [message]
 * honest about the actual cost: most guarded actions here lose context, not saved data.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}
