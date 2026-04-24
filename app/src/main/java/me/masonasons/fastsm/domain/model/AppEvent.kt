package me.masonasons.fastsm.domain.model

/**
 * An application-level event that the speech / sound / haptic subsystems may
 * choose to react to. Keep the shape simple — no internal plumbing types,
 * no View-specific data. Each event carries only what a subsystem would need
 * to compose a useful announcement or play the right sound.
 *
 * Events are named and grouped to mirror the desktop FastSM soundpack event
 * set so existing soundpacks can be ported directly.
 */
sealed interface AppEvent {
    /** Stable key used to look up per-event settings and sound filenames. */
    val key: String

    /** User sent a top-level post. */
    data object PostSent : AppEvent { override val key = "send_post" }
    /** User sent a reply. */
    data object ReplySent : AppEvent { override val key = "send_reply" }
    /** User sent a boost (reblog). */
    data object BoostSent : AppEvent { override val key = "send_repost" }
    /** A post send/edit failed. */
    data class PostFailed(val message: String) : AppEvent { override val key = "error" }

    /**
     * A new post arrived via streaming. [specId] is the tab it landed in;
     * [soundKey] is the sound-file base name, chosen per timeline type (e.g.
     * "home", "user", "list") so each timeline can have its own earcon.
     */
    data class NewPostReceived(val specId: String, val soundKey: String) : AppEvent {
        override val key: String get() = soundKey
    }
    /** A new notification arrived. [text] is the spoken summary if TTS is on. */
    data class NotificationReceived(val text: String) : AppEvent { override val key = "notification" }

    /** A timeline finished a refresh (pull-to-refresh or first load). [count] is visible size. */
    data class TabLoaded(val specId: String, val count: Int) : AppEvent { override val key = "ready" }

    /** Favourite/unfavourite acknowledged. */
    data object Favourited : AppEvent { override val key = "like" }
    data object Unfavourited : AppEvent { override val key = "unlike" }
    /** Bookmark acknowledged. Shares the "like" earcon since the default pack has no bookmark sound. */
    data object Bookmarked : AppEvent { override val key = "like" }
    /** Follow/unfollow acknowledged. */
    data object Followed : AppEvent { override val key = "follow" }
    data object Unfollowed : AppEvent { override val key = "unfollow" }
    /** Post deleted. */
    data object Deleted : AppEvent { override val key = "delete" }
    /** A generic error announcement. */
    data class Error(val message: String) : AppEvent { override val key = "error" }
}
