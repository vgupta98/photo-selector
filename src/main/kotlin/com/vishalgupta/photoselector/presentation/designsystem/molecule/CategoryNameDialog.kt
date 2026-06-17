package com.vishalgupta.photoselector.presentation.designsystem.molecule

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction

/**
 * Hard cap on a category name's length. The rail and top bar both ellipsise an over-long name, but
 * capping the input keeps names sane at the source (and a 40-char name already overflows the rail).
 */
const val MAX_CATEGORY_NAME_LENGTH = 40

/**
 * A single-field dialog for naming a category, used for both create and rename. [title]
 * and [confirmLabel] distinguish the two; [initialName] pre-fills for rename. Confirm is
 * disabled while the field is blank; Enter confirms. Input is capped at [MAX_CATEGORY_NAME_LENGTH].
 *
 * [takenNames] are the names already in use that this name would collide with (for rename, the
 * caller excludes the category's own current name so re-confirming an unchanged name is allowed).
 * A case-insensitive, trimmed match against them disables confirm and shows an inline error, so two
 * categories can never end up sharing a name.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CategoryNameDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
    takenNames: Set<String> = emptySet(),
) {
    var name by remember { mutableStateOf(initialName) }
    val focusRequester = remember { FocusRequester() }
    // Auto-focus the field, but don't let a focus request crash the dialog if the target isn't
    // attached yet (e.g. the popup hasn't placed its content) — losing the auto-focus is harmless.
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    val trimmed = name.trim()
    val isDuplicate = trimmed.isNotEmpty() && takenNames.any { it.equals(trimmed, ignoreCase = true) }
    val canConfirm = trimmed.isNotEmpty() && !isDuplicate
    val confirm = { if (canConfirm) onConfirm(trimmed) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= MAX_CATEGORY_NAME_LENGTH) name = it },
                singleLine = true,
                isError = isDuplicate,
                label = { Text("Name") },
                supportingText = if (isDuplicate) {
                    { Text("A category named “$trimmed” already exists") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(onClick = confirm, enabled = canConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
