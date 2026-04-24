package me.masonasons.fastsm.domain.model

/**
 * User-facing custom accessibility actions that can be toggled per-post in
 * settings. The enum is the source of truth for both the Settings UI (what to
 * show as toggles) and the StatusItem/NotificationItem composables (which
 * actions to include).
 *
 * [key] is the stable id persisted in SharedPreferences — never change it.
 */
enum class PostAction(val key: String, val label: String) {
    REPLY("reply", "Reply"),
    QUOTE("quote", "Quote"),
    FAVOURITE("favourite", "Favourite / unfavourite"),
    BOOST("boost", "Boost / unboost"),
    BOOKMARK("bookmark", "Bookmark / remove bookmark"),
    EDIT("edit", "Edit (own posts)"),
    DELETE("delete", "Delete (own posts)"),
    VIEW_PROFILE("view_profile", "View profile"),
    VIEW_MEDIA("view_media", "View media"),
    OPEN_LINK("open_link", "Open link");

    companion object {
        val ALL: Set<PostAction> = values().toSet()
        fun fromKey(key: String): PostAction? = values().firstOrNull { it.key == key }
    }
}
