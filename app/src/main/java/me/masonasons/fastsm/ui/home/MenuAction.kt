package me.masonasons.fastsm.ui.home

import androidx.compose.ui.semantics.CustomAccessibilityAction

/**
 * A single contextual action on an item (post, tab, account). Rendered in
 * two places:
 *
 *  - as a [CustomAccessibilityAction] for TalkBack's custom-action menu
 *  - as a row in a visible [androidx.compose.material3.DropdownMenu]
 *    triggered by tap (for accounts) or long-press (for posts, tabs, etc.)
 *
 * Keeping both paths out of the same list guarantees they can't drift — a
 * feature that shows up in the TalkBack menu also shows up in the touch menu.
 */
data class MenuAction(val label: String, val run: () -> Unit)

fun List<MenuAction>.toAccessibilityActions(): List<CustomAccessibilityAction> =
    map { a -> CustomAccessibilityAction(a.label) { a.run(); true } }
