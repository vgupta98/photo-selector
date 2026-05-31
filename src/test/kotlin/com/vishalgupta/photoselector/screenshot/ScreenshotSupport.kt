package com.vishalgupta.photoselector.screenshot

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import java.io.File
import javax.imageio.ImageIO

/**
 * Render whatever is currently set on the compose test rule to a PNG under
 * `build/screenshots/<name>.png`. Returns the file so the test can also
 * assert on it (size, existence, diff against a golden, etc.).
 *
 * Compose Desktop's test rule supports `onRoot().captureToImage()` headlessly
 * — no display server needed. The resulting [ImageBitmap] is converted to a
 * `BufferedImage` and written via ImageIO.
 */
fun ComposeContentTestRule.dumpScreenshot(name: String): File = dumpScreenshot(name, onRoot())

/**
 * Capture a specific [node] instead of the whole root. Needed for overlay content
 * (`DropdownMenu`, dialogs) which renders in its own popup root — `onRoot()` would only
 * see the base screen, so the caller passes the popup root (e.g. `onAllNodes(isRoot())
 * .onLast()`).
 */
fun ComposeContentTestRule.dumpScreenshot(name: String, node: SemanticsNodeInteraction): File {
    val image: ImageBitmap = node.captureToImage()
    val awt = image.toAwtImage()
    val outDir = File("build/screenshots").apply { mkdirs() }
    val file = File(outDir, "$name.png")
    ImageIO.write(awt, "png", file)
    return file
}
