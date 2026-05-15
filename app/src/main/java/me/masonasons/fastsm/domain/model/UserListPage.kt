package me.masonasons.fastsm.domain.model

/**
 * A page of users returned from a followers/following query. [nextCursor] is
 * the platform-specific paging token to feed back into the next call — null
 * when the server signaled end-of-list. Mastodon derives the cursor from the
 * `Link: rel="next"` header; Bluesky uses its native cursor.
 */
data class UserListPage(
    val users: List<UniversalUser>,
    val nextCursor: String?,
)
