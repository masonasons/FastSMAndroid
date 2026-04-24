package me.masonasons.fastsm.domain.model

data class UniversalUser(
    val id: String,
    val acct: String,
    val username: String,
    val displayName: String,
    val note: String = "",
    val avatar: String? = null,
    val header: String? = null,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val statusesCount: Int = 0,
    val url: String? = null,
    val bot: Boolean = false,
    val locked: Boolean = false,
    val platform: PlatformType,
)
