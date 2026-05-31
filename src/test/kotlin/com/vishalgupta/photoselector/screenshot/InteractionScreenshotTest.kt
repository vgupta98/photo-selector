package com.vishalgupta.photoselector.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.vishalgupta.photoselector.presentation.designsystem.theme.AppTheme
import org.junit.Rule
import org.junit.Test

/**
 * Demonstrates the typical screenshot-driven UI loop:
 *  1. Set composable content.
 *  2. Dump a baseline screenshot.
 *  3. Drive an interaction via the test rule (`performClick`, `performTextInput`, …).
 *  4. Dump an "after" screenshot.
 *  5. Eyeball the diff (or, later, run an image diff against a golden).
 *
 * Useful precisely for the things type-checking can't verify: text shows up,
 * theming applies, layout doesn't clip, hover/focus states render.
 */
class InteractionScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun toggle_button_updates_label() {
        rule.setContent {
            AppTheme {
                Surface {
                    Box(Modifier.size(480.dp, 240.dp), contentAlignment = Alignment.Center) {
                        var on by remember { mutableStateOf(false) }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(if (on) "Favourited" else "Not favourited")
                            Button(onClick = { on = !on }) {
                                Text(if (on) "Unfavourite" else "Favourite")
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.dumpScreenshot("favourite-before")

        rule.onNodeWithText("Favourite").performClick()
        rule.waitForIdle()
        rule.dumpScreenshot("favourite-after")

        // Assert the UI actually flipped — the screenshot proves it visually,
        // this proves it in the semantics tree too.
        rule.onNodeWithText("Favourited").assertExists()
        rule.onNodeWithText("Unfavourite").assertExists()
    }
}
