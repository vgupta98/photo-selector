package com.vishalgupta.photoselector.presentation.designsystem.atom

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Indeterminate progress spinner. Thin wrapper over [CircularProgressIndicator]
 * so spinners come from one place; size via [modifier].
 */
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    CircularProgressIndicator(modifier)
}
