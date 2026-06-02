package com.vishalgupta.photoselector.presentation.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Centralized type scale. Built on the platform-native sans (SF on macOS, Segoe on
 * Windows, resolved by Skia's default family) rather than a bundled face, but with a
 * deliberate scale layered over Material's generic defaults: titles carry real weight and
 * tightened tracking so headers read as crafted, and body/label tracking is pulled in for a
 * denser, more intentional desktop feel. Only the styles the app actually uses are tuned;
 * the rest fall through to the Material baseline.
 */
private val Default = Typography()

val AppTypography = Default.copy(
    // Display title (root picker). Tighter tracking + weight reads intentional, not default.
    headlineLarge = Default.headlineLarge.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
    ),
    // Top-bar scope identity. Semibold + negative tracking anchors the bar.
    titleLarge = Default.titleLarge.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    ),
    // Browser counter, dialog titles.
    titleMedium = Default.titleMedium.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    ),
    // Captions, paths, instructions — slightly tighter than Material's 0.25sp.
    bodyMedium = Default.bodyMedium.copy(
        letterSpacing = 0.1.sp,
    ),
    // Button text.
    labelLarge = Default.labelLarge.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
    ),
    // Keyboard-legend caps + labels: a touch of tracking keeps tiny text legible.
    labelMedium = Default.labelMedium.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
    ),
)
