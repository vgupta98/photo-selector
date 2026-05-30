package com.vishalgupta.photoselector.presentation.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * App-specific semantic colors that don't map cleanly onto Material3's
 * [androidx.compose.material3.ColorScheme]. Anything that *does* map
 * (primary, surface, error, onSurfaceVariant, …) belongs in [DarkColorScheme]
 * and should be read via `MaterialTheme.colorScheme` instead of duplicated here.
 *
 * Read these through `AppTheme.colors`.
 */
@Immutable
data class AppColors(
    /** Gold accent for the favourite star and the favourited toast. */
    val favourite: Color,
    /** Backdrop behind a photo thumbnail while it decodes. */
    val tileBackground: Color,
    /** Neutral pill-toast background. */
    val toastBackground: Color,
    /** Neutral pill-toast foreground. */
    val toastContent: Color,
    /** Favourited pill-toast background. */
    val favouriteToastBackground: Color,
    /** Favourited pill-toast foreground. */
    val favouriteToastContent: Color,
    /** Translucent scrim over the photo behind the browser top bar. */
    val topBarScrim: Color,
    /** Vertical scrollbar, idle. */
    val scrollbarIdle: Color,
    /** Vertical scrollbar, hovered. */
    val scrollbarHover: Color,
)

/** The single dark palette. Values preserved verbatim from the pre-token UI. */
val DarkAppColors = AppColors(
    favourite = Color(0xFFE9A93C),
    tileBackground = Color(0xFF1E1E1E),
    toastBackground = Color(0xFF2A2A2A),
    toastContent = Color(0xFFE6E6E6),
    favouriteToastBackground = Color(0xFFE9A93C),
    favouriteToastContent = Color(0xFF1A1A1A),
    topBarScrim = Color.Black.copy(alpha = 0.55f),
    scrollbarIdle = Color.White.copy(alpha = 0.3f),
    scrollbarHover = Color.White.copy(alpha = 0.6f),
)

/** Material3 color scheme. Kept on the baseline dark defaults to preserve identity. */
val DarkColorScheme = darkColorScheme()

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }
