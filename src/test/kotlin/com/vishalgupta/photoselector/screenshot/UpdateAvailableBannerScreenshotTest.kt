package com.vishalgupta.photoselector.screenshot

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.vishalgupta.photoselector.presentation.designsystem.molecule.UpdateAvailableBanner
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Eyeball surface for [UpdateAvailableBanner]. Two variants: the normal case (Skip + Later + Download,
 * plus a Release-notes link) and the mandatory case (no Skip — a forced update can't be skipped, only
 * deferred for the session). Eyeball `build/screenshots/update-banner-*.png`.
 */
class UpdateAvailableBannerScreenshotTest {

    @get:Rule val rule = createComposeRule()

    @Test fun `optional update shows skip, later, download and release notes`() {
        rule.setContent {
            AppTheme {
                UpdateAvailableBanner(
                    versionLabel = "1.7.0",
                    mandatory = false,
                    onDownload = {},
                    onLater = {},
                    onSkip = {},
                    onViewNotes = {},
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        rule.waitForIdle()
        val file = rule.dumpScreenshot("update-banner-optional")
        assertTrue(file.exists() && file.length() > 0, "banner screenshot should be written")
    }

    @Test fun `mandatory update hides skip`() {
        rule.setContent {
            AppTheme {
                UpdateAvailableBanner(
                    versionLabel = "2.0.0",
                    mandatory = true,
                    onDownload = {},
                    onLater = {},
                    onSkip = null,
                    onViewNotes = null,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        rule.waitForIdle()
        val file = rule.dumpScreenshot("update-banner-mandatory")
        assertTrue(file.exists() && file.length() > 0, "mandatory banner screenshot should be written")
    }
}
