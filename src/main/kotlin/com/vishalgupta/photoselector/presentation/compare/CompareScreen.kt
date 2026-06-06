package com.vishalgupta.photoselector.presentation.compare

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.vishalgupta.photoselector.domain.model.Category
import com.vishalgupta.photoselector.domain.model.CategoryId
import com.vishalgupta.photoselector.presentation.browser.rememberZoomState
import com.vishalgupta.photoselector.presentation.common.SystemActions
import com.vishalgupta.photoselector.presentation.common.customCategories
import com.vishalgupta.photoselector.presentation.common.digitSlot
import com.vishalgupta.photoselector.presentation.designsystem.molecule.CompareKeyboardLegend
import com.vishalgupta.photoselector.presentation.designsystem.organism.ComparePaneView
import com.vishalgupta.photoselector.presentation.designsystem.organism.BrowserCategoryHud
import com.vishalgupta.photoselector.presentation.designsystem.organism.TopBarScaffold
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme

@Composable
fun CompareScreen(
    viewModel: CompareViewModel,
    systemActions: SystemActions,
    onExit: () -> Unit,
) {
    DisposableEffect(viewModel) { onDispose { viewModel.onClear() } }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

    CompareScreen(
        state = state,
        systemActions = systemActions,
        onSetActive = viewModel::setActive,
        onToggleActive = viewModel::toggleActive,
        onAdvanceActive = viewModel::advanceActive,
        onToggleCategory = viewModel::toggleCategory,
        onViewportSizeChanged = viewModel::setViewportLongEdgePx,
        onExit = onExit,
    )
}

/**
 * Stateless two-up compare. The two panes share a single [rememberZoomState] so zoom and pan move
 * in lockstep — the synchronized pixel-peeping that makes compare worth having. Keyboard model:
 * `Tab` switches the active pane, `← →` substitutes the active pane's photo, `F`/`1–9` file the
 * active pane, `+ − 0` zoom both, `Esc` exits. Zoom deliberately does *not* reset when a pane is
 * substituted, so the user keeps the same crop while swapping candidates.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CompareScreen(
    state: CompareUiState,
    systemActions: SystemActions? = null,
    onSetActive: (ComparePaneSide) -> Unit,
    onToggleActive: () -> Unit,
    onAdvanceActive: (Int) -> Unit,
    onToggleCategory: (CategoryId) -> Unit,
    onViewportSizeChanged: (Int) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val zoom = rememberZoomState()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    var viewportPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(viewportPx) {
        if (viewportPx > 0) onViewportSizeChanged(viewportPx)
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val meta = event.isMetaPressed
                val slot = if (meta) null else digitSlot(event.key)
                if (slot != null) {
                    state.categories.customCategories().getOrNull(slot)?.let { onToggleCategory(it.id) }
                    return@onPreviewKeyEvent true
                }
                when (event.key) {
                    Key.Tab -> { onToggleActive(); true }
                    Key.DirectionLeft -> { onAdvanceActive(-1); true }
                    Key.DirectionRight -> { onAdvanceActive(1); true }
                    Key.F -> if (meta) false else { onToggleCategory(Category.FAVOURITES_ID); true }
                    Key.Spacebar -> if (meta) false else {
                        state.active.photo?.absolutePath?.let { systemActions?.preview(it) }
                        true
                    }
                    Key.R -> if (meta) false else {
                        state.active.photo?.absolutePath?.let { systemActions?.revealInFileManager(it) }
                        true
                    }
                    Key.O -> if (meta) false else {
                        state.active.photo?.absolutePath?.let { systemActions?.openWithDefaultApp(it) }
                        true
                    }
                    Key.Equals, Key.Plus -> { zoom.zoomIn(); true }
                    Key.Minus -> { zoom.zoomOut(); true }
                    Key.Zero -> { zoom.reset(); true }
                    Key.Escape -> { onExit(); true }
                    else -> false
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBarScaffold(
                modifier = Modifier.fillMaxWidth(),
                containerColor = AppTheme.colors.topBarScrim,
            ) {
                IconButton(onClick = onExit) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text("Compare", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }

            Box(Modifier.fillMaxWidth().weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { size ->
                            // Each pane gets ~half the width; size the decode to the larger pane edge.
                            val perPane = maxOf(size.width / 2, size.height)
                            if (perPane > 0) viewportPx = perPane
                        },
                ) {
                    ComparePaneView(
                        pane = state.left,
                        isActive = state.activeSide == ComparePaneSide.LEFT,
                        totalInScope = state.totalInScope,
                        zoom = zoom,
                        onActivate = { onSetActive(ComparePaneSide.LEFT) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(AppTheme.colors.overlayChromeInactiveFill),
                    )
                    ComparePaneView(
                        pane = state.right,
                        isActive = state.activeSide == ComparePaneSide.RIGHT,
                        totalInScope = state.totalInScope,
                        zoom = zoom,
                        onActivate = { onSetActive(ComparePaneSide.RIGHT) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }

                // Bottom chrome reflects the ACTIVE pane: the HUD lights its memberships and toggles
                // them, the legend names the keys. Kept persistent (compare is a short, deliberate
                // mode) rather than auto-hidden like the browser.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = AppTheme.spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
                ) {
                    BrowserCategoryHud(
                        categories = state.categories,
                        currentMemberships = state.active.memberships,
                        onToggle = onToggleCategory,
                    )
                    CompareKeyboardLegend(
                        hasCustomCategories = state.categories.customCategories().isNotEmpty(),
                        readOnly = state.readOnly,
                    )
                }
            }
        }
    }
}
