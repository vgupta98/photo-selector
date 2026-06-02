package com.vishalgupta.photoselector.presentation.designsystem.organism

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.vishalgupta.photoselector.data.image.ImageLoader
import com.vishalgupta.photoselector.domain.model.Photo
import com.vishalgupta.photoselector.presentation.designsystem.atom.FavouriteStar
import com.vishalgupta.photoselector.presentation.designsystem.atom.LoadingIndicator
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

private const val THUMBNAIL_VIEWPORT_PX = 320

/**
 * A square photo tile: decoded image (cropped to fill), an optional star marking
 * Favourites membership (in any scope, including a custom-category grid), a focus
 * border, and a "last viewed" underline. Decodes lazily through [loader], keyed on the
 * photo id.
 *
 * [categoryBadges] are the digit slots (1..9) of the custom categories this photo belongs
 * to — shown as small numbered chips so filing a photo with a number key leaves a visible,
 * inspectable mark (favourites stays the star). The list type is immutable so the tile stays
 * skippable: a membership change elsewhere doesn't churn this tile.
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
    categoryBadges: ImmutableList<Int> = persistentListOf(),
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
        if (categoryBadges.isNotEmpty()) {
            CategoryBadges(
                badges = categoryBadges,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(AppTheme.spacing.xs),
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

/** Max chips drawn before collapsing the tail into a "+N" overflow chip. */
private const val MAX_VISIBLE_BADGES = 4

/**
 * The custom-category slots a photo is filed in, as small numbered chips. The number is the
 * `1..9` key that toggles that category, so the badge doubles as a reminder of the hotkey.
 * Capped so a heavily-filed photo can't blow out the tile.
 */
@Composable
private fun CategoryBadges(badges: ImmutableList<Int>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs),
    ) {
        badges.take(MAX_VISIBLE_BADGES).forEach { slot -> CategoryBadge(slot.toString()) }
        val overflow = badges.size - MAX_VISIBLE_BADGES
        if (overflow > 0) CategoryBadge("+$overflow")
    }
}

@Composable
private fun CategoryBadge(label: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = AppTheme.colors.categoryMemberContainer,
        contentColor = AppTheme.colors.categoryMemberContent,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}
