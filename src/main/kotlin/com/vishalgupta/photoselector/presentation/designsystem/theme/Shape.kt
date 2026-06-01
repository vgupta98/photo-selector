package com.vishalgupta.photoselector.presentation.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Material3 shape scale used for surfaces, tiles and buttons. */
val AppShapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
)

/** Fully-rounded pill, used by toasts. Not part of the Material [Shapes] scale. */
val PillShape = RoundedCornerShape(percent = 50)
