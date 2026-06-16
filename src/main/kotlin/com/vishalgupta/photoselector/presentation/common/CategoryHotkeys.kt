package com.vishalgupta.photoselector.presentation.common

import androidx.compose.ui.input.key.Key
import com.vishalgupta.photoselector.domain.model.Category

/**
 * The keyboard model for category membership, shared by the grid and the browser:
 *
 * - `F` / `Space` toggles the built-in **Favourites** everywhere.
 * - bare `1`..`9` toggles the Nth **custom** category in display order — slot 0 is the
 *   first non-built-in category. The 10th-plus custom categories have no digit and are
 *   reached from the grid's Categories dropdown.
 *
 * Keeping the digit→slot map and the custom-ordering in one place stops the grid keys, the
 * browser keys, and the HUD legend from drifting apart.
 */

/** Custom (non-built-in) categories in display order — the ones digit keys 1..9 bind to. */
fun List<Category>.customCategories(): List<Category> = filter { !it.builtIn }

/**
 * The leading digit hint for the [slot]th custom category in a menu (`"1  "` .. `"9  "`), or
 * `""` past slot 8. The visual counterpart of [digitSlot]: it keeps a menu entry's number
 * matching the key that files into it, so the All-Photos and selection menus never disagree on
 * the numbering.
 */
fun categorySlotPrefix(slot: Int): String = if (slot < 9) "${slot + 1}  " else ""

/** Maps a number-row key to a zero-based digit slot (`1`→0 .. `9`→8), or null for non-digits. */
fun digitSlot(key: Key): Int? = when (key) {
    Key.One -> 0
    Key.Two -> 1
    Key.Three -> 2
    Key.Four -> 3
    Key.Five -> 4
    Key.Six -> 5
    Key.Seven -> 6
    Key.Eight -> 7
    Key.Nine -> 8
    else -> null
}
