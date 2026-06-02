package com.vishalgupta.photoselector.presentation.designsystem.organism

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.vishalgupta.photoselector.data.image.ImageLoader
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.presentation.designsystem.atom.FavouriteStar
import com.vishalgupta.photoselector.presentation.designsystem.atom.LoadingIndicator
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme

private const val THUMBNAIL_VIEWPORT_PX = 320

/**
 * A square photo tile: decoded image (cropped to fill), an optional star marking
 * Favourites membership (in any scope, including a custom-category grid), a focus
 * border, and a "last viewed" underline. Decodes lazily through [loader], keyed on the
 * photo id.
 */
@Composable
fun PhotoThumbnail(
    photo: Photo,
    loader: ImageLoader,
    isMarked: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLastViewed: Boolean = false,
) {
    val bitmap by produceState<ImageBitmap?>(null, photo.id) {
        value = loader.load(photo, viewportLongEdgePx = THUMBNAIL_VIEWPORT_PX)
    }
    val borderMod = if (isFocused) {
        Modifier.border(
            AppTheme.dimens.focusBorderWidth,
            AppTheme.colors.focusRing,
            MaterialTheme.shapes.small,
        )
    } else {
        Modifier
    }
    Box(
        modifier
            .aspectRatio(1f)
            .then(borderMod)
            .background(AppTheme.colors.tileBackground)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = photo.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LoadingIndicator()
        }
        if (isMarked) {
            FavouriteStar(
                filled = true,
                tint = AppTheme.colors.favourite,
                contentDescription = "Favourited",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(AppTheme.spacing.xs)
                    .size(AppTheme.dimens.iconSm),
            )
        }
        if (isLastViewed) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(AppTheme.dimens.lastViewedIndicatorHeight)
                    .background(AppTheme.colors.lastViewedIndicator),
            )
        }
    }
}
