package me.masonasons.fastsm.domain.model

/** A user-owned list (for the "Add list timeline" picker). */
data class UserListInfo(
    val id: String,
    val title: String,
    val platform: PlatformType,
)
