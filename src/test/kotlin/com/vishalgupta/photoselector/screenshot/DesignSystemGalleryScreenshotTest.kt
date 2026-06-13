package com.vishalgupta.photoselector.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.vishalgupta.photoselector.domain.model.Category
import com.vishalgupta.photoselector.domain.model.CategoryId
import com.vishalgupta.photoselector.presentation.designsystem.atom.AppButton
import com.vishalgupta.photoselector.presentation.designsystem.atom.AppOutlinedButton
import com.vishalgupta.photoselector.presentation.designsystem.atom.AppTextButton
import com.vishalgupta.photoselector.presentation.designsystem.atom.FavouriteStar
import com.vishalgupta.photoselector.presentation.designsystem.atom.LoadingIndicator
import com.vishalgupta.photoselector.presentation.designsystem.molecule.BusyBar
import com.vishalgupta.photoselector.presentation.designsystem.molecule.CategoryMenu
import com.vishalgupta.photoselector.presentation.designsystem.molecule.ConflictPolicyButton
import com.vishalgupta.photoselector.presentation.designsystem.molecule.ErrorPlaceholder
import com.vishalgupta.photoselector.presentation.designsystem.molecule.FavouritesButton
import com.vishalgupta.photoselector.presentation.designsystem.molecule.PillToast
import com.vishalgupta.photoselector.presentation.designsystem.molecule.PillToastDefaults
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Renders the design-system atoms and molecules in their key states and dumps a
 * single gallery PNG to `build/screenshots/`. This is the eyeball surface for
 * the token + component layer — when a token changes, this is the screenshot to
 * diff. Screen-level organisms are covered by [ScreenSplitScreenshotTest].
 */
class DesignSystemGalleryScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun design_system_gallery() {
        rule.setContent {
            AppTheme {
                Surface(Modifier.size(720.dp, 760.dp)) {
                    Column(
                        Modifier.fillMaxWidth().padding(AppTheme.spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.lg),
                    ) {
                        Text("Buttons", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)) {
                            AppButton(text = "Primary", onClick = {})
                            AppOutlinedButton(text = "Outlined", onClick = {})
                            AppTextButton(text = "Change folder", leadingIcon = Icons.Default.Folder, onClick = {})
                            AppButton(text = "Disabled", enabled = false, onClick = {})
                        }

                        Text("Favourite star", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.md)) {
                            FavouriteStar(filled = true, tint = AppTheme.colors.favourite, modifier = Modifier.size(AppTheme.dimens.iconSm))
                            FavouriteStar(filled = false, modifier = Modifier.size(AppTheme.dimens.iconSm))
                            FavouritesButton(count = 12, onClick = {})
                            ConflictPolicyButton(enabled = true, onSelect = {})
                        }

                        // The Favourites pill and the Categories dropdown trigger sit side by side
                        // in the real top bar; the caret on Categories is what tells them apart.
                        Text("Top-bar category controls", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.md)) {
                            FavouritesButton(count = 40, onClick = {})
                            CategoryMenu(
                                entries = listOf(
                                    Category.favourites() to 40,
                                    Category(CategoryId("c1"), "MyCategory", false) to 16,
                                    Category(CategoryId("c2"), "MyCategory2", false) to 19,
                                ),
                                onSelect = {},
                                onCreateRequested = {},
                            )
                        }

                        Text("Pill toasts", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.md)) {
                            PillToast(
                                text = "Favourited",
                                leadingIcon = { FavouriteStar(filled = true, modifier = Modifier.size(AppTheme.dimens.iconSm)) },
                                colors = PillToastDefaults.favouriteColors(),
                            )
                            PillToast(
                                text = "Unfavourited",
                                leadingIcon = { FavouriteStar(filled = false, modifier = Modifier.size(AppTheme.dimens.iconSm)) },
                            )
                        }

                        Text("Busy bar", style = MaterialTheme.typography.titleMedium)
                        BusyBar(label = "Copying… 34 / 120")

                        Text("Loading + placeholder", style = MaterialTheme.typography.titleMedium)
                        Row(
                            Modifier.fillMaxWidth().height(160.dp),
                            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.lg),
                        ) {
                            LoadingIndicator()
                            ErrorPlaceholder("No favourites yet.", Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val file = rule.dumpScreenshot("design-system-gallery")
        assertTrue(file.exists() && file.length() > 0, "gallery screenshot should be written")
    }
}
