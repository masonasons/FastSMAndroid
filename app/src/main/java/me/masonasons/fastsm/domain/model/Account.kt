package me.masonasons.fastsm.domain.model

/**
 * Identity + instance pairing. One per logged-in account.
 * The access token lives in EncryptedSharedPreferences, keyed by [id].
 */
data class Account(
    val id: Long,
    val platform: PlatformType,
    val instance: String,
    val userId: String,
    val acct: String,
    val displayName: String,
    val avatar: String? = null,
) {
    val label: String get() = "@$acct"
}
