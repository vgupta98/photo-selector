package com.vishalgupta.photoselector.presentation.designsystem.molecule

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.vishalgupta.photoselector.presentation.designsystem.atom.AppTextButton

/**
 * The "Change folder" action, shared by the grid and browser top bars. Owns both the
 * button and the confirm dialog that guards it so the change-folder contract — copy and
 * guard wiring — lives in exactly one place and can't drift between the two bars.
 *
 * Callers pass only [onChangeFolder]; the confirm state stays inside the molecule. The
 * only thing that varies between bars is [contentColor] (muted white over the browser
 * scrim vs `onSurfaceVariant` on the grid's opaque bar); `null` falls back to
 * [AppTextButton]'s default accent.
 */
@Composable
fun ChangeFolderButton(
    onChangeFolder: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color? = null,
) {
    var showConfirm by remember { mutableStateOf(false) }
    AppTextButton(
        text = "Change folder",
        leadingIcon = Icons.Default.Folder,
        onClick = { showConfirm = true },
        contentColor = contentColor,
        modifier = modifier,
    )
    if (showConfirm) {
        ConfirmDialog(
            title = "Change folder?",
            message = "You'll leave this folder and pick a new one to open. " +
                "Your favourites and categories are saved.",
            confirmLabel = "Change folder",
            onConfirm = {
                showConfirm = false
                onChangeFolder()
            },
            onDismiss = { showConfirm = false },
        )
    }
}
