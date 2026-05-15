package me.masonasons.fastsm.domain.model

data class Relationship(
    val userId: String,
    val following: Boolean,
    val requested: Boolean,
    val followedBy: Boolean,
    val muting: Boolean,
    val blocking: Boolean,
    /**
     * Whether boosts/reposts from this user appear in the home timeline.
     * Mastodon-only — Bluesky has no per-account boost toggle, so we report
     * `true` there. Only meaningful when [following] is true.
     */
    val showReblogs: Boolean = true,
) {
    val followState: FollowState get() = when {
        following -> FollowState.FOLLOWING
        requested -> FollowState.REQUESTED
        else -> FollowState.NOT_FOLLOWING
    }
}

enum class FollowState { NOT_FOLLOWING, REQUESTED, FOLLOWING }
