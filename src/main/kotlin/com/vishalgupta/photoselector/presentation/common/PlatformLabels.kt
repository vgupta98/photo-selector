package com.vishalgupta.photoselector.presentation.common

/**
 * OS-aware copy for the system actions whose product names differ by platform. Resolving a
 * *label* from the host OS is a presentation concern; the behaviour stays behind
 * [SystemActions] and is unchanged. macOS is the only platform wired today — the Windows arm
 * is the correct copy for the planned Windows build, so the chrome already reads right when a
 * Windows `SystemActions` is added later (see CLAUDE.md / the redesign brief, out of scope here).
 */
object PlatformLabels {
    private val isMac: Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("mac")

    /** Menu label for "reveal this file in the OS file manager". */
    val revealInFileManager: String = if (isMac) "Reveal in Finder" else "Show in Explorer"

    /** Menu label for "open with the OS default app" — platform-neutral already. */
    const val OPEN_WITH_DEFAULT_APP: String = "Open with Default App"
}
