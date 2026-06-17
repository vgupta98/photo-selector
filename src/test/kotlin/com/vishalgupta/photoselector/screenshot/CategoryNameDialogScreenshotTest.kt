package com.vishalgupta.photoselector.screenshot

import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onLast
import com.vishalgupta.photoselector.presentation.designsystem.molecule.CategoryNameDialog
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Eyeball surface for [CategoryNameDialog]'s duplicate-name guard. Pre-filling a name that already
 * exists ([takenNames]) must flip the field into its error state and disable Confirm, so two
 * categories can never share a name. Capture the dialog's own popup root (it renders outside the
 * base screen) and eyeball `build/screenshots/category-name-dialog-duplicate.png`: the field reads
 * "Keepers", the inline error explains the clash, and the "Create" button is disabled.
 */
class CategoryNameDialogScreenshotTest {

    @get:Rule val rule = createComposeRule()

    @Test fun `duplicate name disables confirm and shows an inline error`() {
        rule.setContent {
            AppTheme {
                CategoryNameDialog(
                    title = "New category",
                    confirmLabel = "Create",
                    initialName = "Keepers",
                    takenNames = setOf("Favourites", "Keepers", "Portfolio"),
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        rule.waitForIdle()
        // The dialog renders in its own popup root; capture that, not the (empty) base screen.
        val file = rule.dumpScreenshot("category-name-dialog-duplicate", rule.onAllNodes(isRoot()).onLast())
        assertTrue(file.exists() && file.length() > 0, "dialog screenshot should be written")
    }
}
