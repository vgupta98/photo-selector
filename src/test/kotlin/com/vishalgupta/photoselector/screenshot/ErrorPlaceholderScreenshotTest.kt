package com.vishalgupta.photoselector.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.vishalgupta.photoselector.presentation.common.ErrorPlaceholder
import com.vishalgupta.photoselector.presentation.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class ErrorPlaceholderScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_error_placeholder_with_message() {
        rule.setContent {
            AppTheme {
                Surface {
                    Box(Modifier.size(width = 480.dp, height = 320.dp)) {
                        ErrorPlaceholder(message = "Couldn't decode photo")
                    }
                }
            }
        }
        rule.waitForIdle()
        val file = rule.dumpScreenshot("error-placeholder")
        assertTrue(file.exists(), "screenshot file should exist")
        assertTrue(file.length() > 0, "screenshot file should not be empty")
    }
}
