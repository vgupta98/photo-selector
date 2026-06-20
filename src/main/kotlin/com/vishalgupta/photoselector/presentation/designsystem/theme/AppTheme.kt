package com.vishalgupta.photoselector.presentation.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * The single entry point for every design token. App-specific tokens
 * ([colors]/[spacing]/[dimens]) live in their own composition locals; the
 * Material-mapped tokens ([colorScheme]/[typography]/[shapes]) are re-exposed
 * here as thin delegates to [MaterialTheme] so call sites never reach into
 * `MaterialTheme` directly — read everything through `AppTheme`.
 *
 *     AppTheme.colors.favourite
 *     AppTheme.spacing.md
 *     AppTheme.dimens.topBarHeight
 *     AppTheme.colorScheme.onSurfaceVariant
 *     AppTheme.typography.titleLarge
 *     AppTheme.shapes.small
 *
 * `MaterialTheme` itself appears only once, in [AppTheme]'s own setup below
 * (Material components still read it internally) — never at a call site.
 */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current

    val spacing: Spacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current

    val dimens: Dimens
        @Composable @ReadOnlyComposable get() = LocalDimens.current

    // Material-mapped tokens, delegated so callers stay on one accessor. (The values still come
    // from the MaterialTheme provided below; this is access unification, not duplication.)
    val colorScheme: ColorScheme
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme

    val typography: Typography
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography

    val shapes: Shapes
        @Composable @ReadOnlyComposable get() = MaterialTheme.shapes
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
