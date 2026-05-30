package com.vishalgupta.photoselector.presentation.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Accessors for the app's design tokens, mirroring the `MaterialTheme` object
 * pattern. Material-mapped tokens (colorScheme, typography, shapes) are read via
 * [MaterialTheme]; app-specific tokens are read here.
 *
 *     AppTheme.colors.favourite
 *     AppTheme.spacing.md
 *     AppTheme.dimens.topBarHeight
 */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current

    val spacing: Spacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current

    val dimens: Dimens
        @Composable @ReadOnlyComposable get() = LocalDimens.current
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAppColors provides DarkAppColors,
        LocalSpacing provides Spacing(),
        LocalDimens provides Dimens(),
    ) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
