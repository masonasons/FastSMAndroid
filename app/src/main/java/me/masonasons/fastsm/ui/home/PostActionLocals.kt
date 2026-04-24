package me.masonasons.fastsm.ui.home

import androidx.compose.runtime.compositionLocalOf
import me.masonasons.fastsm.domain.model.PostAction
import me.masonasons.fastsm.domain.template.PostTemplateRenderer
import me.masonasons.fastsm.domain.template.TemplateConfig

/**
 * Composition-local for the user-selected set of enabled custom accessibility
 * actions on a post. Provided once at the nav-graph level from [AppPrefs] so
 * StatusItem/NotificationItem don't need the set propagated through every
 * intermediate composable.
 */
val LocalEnabledPostActions = compositionLocalOf { PostAction.ALL }

/** User-configurable template strings + post-processing config used to build
 *  TalkBack announcements. */
data class PostTemplates(
    val post: String = PostTemplateRenderer.DEFAULT_POST,
    val boost: String = PostTemplateRenderer.DEFAULT_BOOST,
    val notification: String = PostTemplateRenderer.DEFAULT_NOTIFICATION,
    val config: TemplateConfig = TemplateConfig(),
)

val LocalPostTemplates = compositionLocalOf { PostTemplates() }

/** When true, compose auto-focuses its text field on open to raise the keyboard. */
val LocalAutoFocusCompose = compositionLocalOf { true }

/** When true, an IME Send action (incl. Braille Keyboard submit) posts. */
val LocalSubmitOnImeAction = compositionLocalOf { false }
