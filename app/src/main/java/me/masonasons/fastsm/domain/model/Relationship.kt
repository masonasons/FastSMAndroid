package me.masonasons.fastsm.domain.model

data class Relationship(
    val userId: String,
    val following: Boolean,
    val requested: Boolean,
    val followedBy: Boolean,
    val muting: Boolean,
    val blocking: Boolean,
) {
    val followState: FollowState get() = when {
        following -> FollowState.FOLLOWING
        requested -> FollowState.REQUESTED
        else -> FollowState.NOT_FOLLOWING
    }
}

enum class FollowState { NOT_FOLLOWING, REQUESTED, FOLLOWING }
