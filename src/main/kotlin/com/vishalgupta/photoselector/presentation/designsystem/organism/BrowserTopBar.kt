package com.vishalgupta.photoselector.presentation.designsystem.organism

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.vishalgupta.photoselector.presentation.designsystem.atom.AppTextButton
import com.vishalgupta.photoselector.presentation.designsystem.molecule.ConfirmDialog
import com.vishalgupta.photoselector.presentation.designsystem.molecule.FavouritesButton
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme

/**
 * Top bar overlaid on the full-screen photo browser. Sits over a translucent
 * scrim; count + path are rendered in white for legibility against the photo.
 */
@Composable
fun BrowserTopBar(
    countLabel: String,
    relativePath: String,
    favouriteCount: Int,
    readOnly: Boolean,
    onBack: () -> Unit,
    onOpenFavourites: () -> Unit,
    onChangeFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showChangeFolderConfirm by remember { mutableStateOf(false) }
    TopBarScaffold(modifier, containerColor = AppTheme.colors.topBarScrim) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to grid",
                tint = Color.White,
            )
        }
        Text(countLabel, color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "—  $relativePath",
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        if (readOnly) {
            Text(
                "Read-only folder · selections in-memory only",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        FavouritesButton(count = favouriteCount, onClick = onOpenFavourites)
        AppTextButton(
            text = "Change folder",
            leadingIcon = Icons.Default.Folder,
            onClick = { showChangeFolderConfirm = true },
            // Muted white so it recedes behind Favourites — the bar's real action — on the scrim.
            contentColor = Color.White.copy(alpha = 0.7f),
        )
    }

    if (showChangeFolderConfirm) {
        ConfirmDialog(
            title = "Change folder?",
            message = "You'll leave this folder and pick a new one to open. " +
                "Your favourites and categories are saved.",
            confirmLabel = "Change folder",
            onConfirm = {
                showChangeFolderConfirm = false
                onChangeFolder()
            },
            onDismiss = { showChangeFolderConfirm = false },
        )
    }
}
