package com.vishalgupta.photoselector.presentation.designsystem.organism

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vishalgupta.photoselector.domain.repository.ConflictPolicy
import com.vishalgupta.photoselector.presentation.designsystem.atom.AppOutlinedButton
import com.vishalgupta.photoselector.presentation.designsystem.atom.AppTextButton
import com.vishalgupta.photoselector.presentation.designsystem.molecule.ConflictPolicyButton
import com.vishalgupta.photoselector.presentation.designsystem.molecule.FavouritesButton
import com.vishalgupta.photoselector.presentation.navigation.BrowseScope

/**
 * Top bar for the photo grid. In [BrowseScope.AllPhotos] it shows a photo count
 * and a jump-to-favourites action; in [BrowseScope.FavouritesOnly] it adds the
 * export / copy actions and a back button.
 */
@Composable
fun GridTopBar(
    scope: BrowseScope,
    photoCount: Int,
    favouriteCount: Int,
    isBusy: Boolean,
    onBack: (() -> Unit)?,
    onOpenFavourites: () -> Unit,
    onExportTxt: () -> Unit,
    onCopyToFolder: (ConflictPolicy) -> Unit,
    onChangeFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasFavourites = favouriteCount > 0 && !isBusy
    TopBarScaffold(modifier) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }

        val title = when (scope) {
            BrowseScope.AllPhotos -> "$photoCount photos"
            BrowseScope.FavouritesOnly -> "Favourites ($photoCount)"
        }
        Text(title, style = MaterialTheme.typography.titleLarge)

        if (scope == BrowseScope.AllPhotos) {
            FavouritesButton(count = favouriteCount, onClick = onOpenFavourites)
        }

        Spacer(Modifier.weight(1f))

        if (scope == BrowseScope.FavouritesOnly) {
            AppOutlinedButton(
                text = "Export list (.txt)",
                enabled = hasFavourites,
                onClick = onExportTxt,
            )
            ConflictPolicyButton(enabled = hasFavourites, onSelect = onCopyToFolder)
        }

        AppTextButton(
            text = "Change folder",
            leadingIcon = Icons.Default.Folder,
            onClick = onChangeFolder,
        )
    }
}
