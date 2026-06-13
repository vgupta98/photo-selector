package com.vishalgupta.photoselector.presentation.designsystem.organism

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
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
 *
 * [isSelected] marks the tile as part of a multi-select (accent ring + scale-down + a check
 * badge). When [onToggleSelect] (Cmd+Click) / [onRangeSelect] (Shift+Click) are supplied, a
 * modified click drives selection while a plain click still falls through to [onClick] — so the
 * established "click a tile to open it" gesture is untouched.
 *
 * [burstCount], when non-null, marks this tile as the collapsed representative of a group of that
 * many frames (a burst or a similarity cluster). The cover photo is drawn inset over a small fanned
 * "deck" of cards so the stack reads as a stack at a glance, with a count pill naming how many; the
 * tile still shows [photo] (the group's key frame), and the caller wires [onClick] to open the run
 * side by side rather than a single browser.
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
    isSelected: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    onRangeSelect: (() -> Unit)? = null,
    categoryBadges: ImmutableList<Int> = persistentListOf(),
    burstCount: Int? = null,
    withinBurst: Boolean = false,
) {
    val bitmap by produceState<ImageBitmap?>(null, photo.id) {
        value = loader.load(photo, viewportLongEdgePx = THUMBNAIL_VIEWPORT_PX)
    }
    // Selection's accent ring takes precedence over the focus cursor on the rare tile that is both;
    // an expanded-burst frame carries a dim bracket ring only when neither of those is present.
    val ringColor = when {
        isSelected -> AppTheme.colors.selectionRing
        isFocused -> AppTheme.colors.focusRing
        withinBurst -> AppTheme.colors.burstFrameRing
        else -> null
    }
    val borderMod = if (ringColor != null) {
        Modifier.border(AppTheme.dimens.focusBorderWidth, ringColor, MaterialTheme.shapes.small)
    } else {
        Modifier
    }
    // Selected tiles ease down a touch so the picked-out set reads at a glance.
    val tileScale by animateFloatAsState(if (isSelected) 0.95f else 1f, label = "tileSelectScale")
    // One plain clickable carries every click; the held modifier is read from window state at
    // click time (NOT during composition, so it never costs the tile a recomposition) and routes
    // it — Cmd toggles selection, Shift ranges, unmodified opens. This keeps the single proven
    // open path and its click semantics, instead of layering a modifier-matching pointer node
    // that races the clickable and lets one swallow the other.
    val windowInfo = LocalWindowInfo.current

    // A collapsed group (burst or similarity cluster) reads as a small fanned deck: a couple of
    // neutral cards peek out top-right behind an inset cover photo, so a stack of frames is legible
    // as a stack without leaning on the count pill alone. The deck is a *background draw* + a content
    // *inset*, deliberately NOT nested layout nodes: the tile stays a single layout node (a single
    // photo is then byte-identical to the pre-deck tile), so the grid measures it in one pass. An
    // earlier nested-Box version looped the measure/draw phase under grid scroll — invisible to the
    // static screenshot tests, caught by GridKeyboardTest. Keep this one Box.
    val isGroup = burstCount != null
    val stackInset = AppTheme.dimens.burstStackInset
    val deckCard = AppTheme.colors.burstStackCard
    val deckEdge = AppTheme.colors.tileBackground
    Box(
        modifier
            .aspectRatio(1f)
            .scale(tileScale)
            .then(
                if (isGroup) {
                    Modifier
                        .drawBehind { drawBurstDeck(stackInset.toPx(), deckCard, deckEdge) }
                        .padding(top = stackInset, end = stackInset)
                } else {
                    Modifier
                },
            )
            .then(borderMod)
            .background(AppTheme.colors.tileBackground)
            .clickable {
                val mods = windowInfo.keyboardModifiers
                when {
                    onToggleSelect != null && mods.isMetaPressed && !mods.isShiftPressed -> onToggleSelect()
                    onRangeSelect != null && mods.isShiftPressed -> onRangeSelect()
                    else -> onClick()
                }
            },
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
        if (burstCount != null) {
            // Bottom-start keeps the burst badge clear of the star (top-end), category badges
            // (top-start) and the select check (bottom-end).
            BurstBadge(
                count = burstCount,
                modifier = Modifier
                    .align(Alignment.BottomStart)
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
        if (isSelected) {
            // Bottom-end keeps the check clear of the star (top-end) and category badges (top-start).
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(AppTheme.spacing.xs)
                    .size(AppTheme.dimens.iconSm)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.fillMaxSize().padding(3.dp),
                )
            }
        }
    }
}

/** Small corner radius for the deck cards, matching the tile's rounded-small look. */
private val DECK_CORNER = 4.dp

/**
 * Paints the two neutral cards behind a collapsed group's (inset) cover photo, stepped up toward the
 * top-right so the tile reads as a stack of frames. Drawn at the full cell bounds (this runs before
 * the cover's `padding`), so only the cards' top/right edges peek past the inset cover. The back card
 * peeks the furthest ([insetPx]); the middle card half that. An [edge] hairline separates each card.
 */
private fun DrawScope.drawBurstDeck(insetPx: Float, card: Color, edge: Color) {
    val cardSize = Size(size.width - insetPx, size.height - insetPx)
    val corner = CornerRadius(DECK_CORNER.toPx())
    val hairline = Stroke(width = 1.dp.toPx())
    // Back-most card: flush to the top-end corner.
    drawDeckCard(Offset(insetPx, 0f), cardSize, corner, card, edge, hairline)
    // Middle card: half a step back toward the cover.
    val half = insetPx / 2f
    drawDeckCard(Offset(half, half), cardSize, corner, card, edge, hairline)
}

private fun DrawScope.drawDeckCard(
    topLeft: Offset,
    cardSize: Size,
    corner: CornerRadius,
    fill: Color,
    edge: Color,
    hairline: Stroke,
) {
    drawRoundRect(color = fill, topLeft = topLeft, size = cardSize, cornerRadius = corner)
    drawRoundRect(color = edge, topLeft = topLeft, size = cardSize, cornerRadius = corner, style = hairline)
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

/**
 * The collapsed-group count pill: a stacked-frames glyph plus the frame count, telling the user
 * this one tile stands in for N frames that open together. Drawn in the translucent-dark
 * overlay-chrome chip style (shared with the browser HUD) with bright text, so it stays legible
 * over both bright and dark photos — louder than a category chip because it changes what a click
 * does (it opens a run, not a single).
 */
@Composable
private fun BurstBadge(count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = AppTheme.colors.overlayChromeBackground,
        contentColor = AppTheme.colors.onOverlayChrome,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = AppTheme.dimens.badgeInset, vertical = AppTheme.spacing.xxs),
        ) {
            Icon(
                imageVector = Icons.Filled.BurstMode,
                contentDescription = "Burst of $count",
                modifier = Modifier.size(AppTheme.dimens.iconSm),
            )
            Text(text = count.toString(), style = MaterialTheme.typography.labelLarge)
        }
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
            modifier = Modifier.padding(horizontal = AppTheme.dimens.badgeInset, vertical = 1.dp),
        )
    }
}
