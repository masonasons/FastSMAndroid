package me.masonasons.fastsm.platform.bluesky

import io.ktor.client.HttpClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.masonasons.fastsm.data.prefs.TokenStore

/**
 * Rotates Bluesky JWTs when the short-lived access token expires. One Mutex
 * per account id so parallel 401s only trigger a single refresh; a brief
 * cooldown window lets other waiters pick up the newly-written token without
 * refreshing again.
 */
class BlueskyAuthCoordinator(
    private val httpClient: HttpClient,
    private val tokenStore: TokenStore,
) {
    private val mutex = Mutex()
    private val lastRefresh = mutableMapOf<Long, Long>()

    /**
     * Attempt a token refresh. Returns true if the stored access/refresh JWTs
     * were updated (or another coroutine refreshed within the cooldown).
     * Returns false if the refresh JWT itself is invalid — caller should
     * surface that as "please log in again".
     */
    suspend fun refresh(accountId: Long, pdsBase: String): Boolean = mutex.withLock {
        val now = System.currentTimeMillis()
        val last = lastRefresh[accountId] ?: 0L
        if (now - last < COOLDOWN_MS) return@withLock true

        val refreshJwt = tokenStore.getRefreshToken(accountId) ?: return@withLock false
        val api = BlueskyApi(httpClient, pdsBase) { refreshJwt }
        val session = runCatching { api.refreshSession(refreshJwt) }.getOrElse { return@withLock false }

        tokenStore.setAccessToken(accountId, session.accessJwt)
        tokenStore.setRefreshToken(accountId, session.refreshJwt)
        lastRefresh[accountId] = System.currentTimeMillis()
        true
    }

    private companion object {
        const val COOLDOWN_MS = 2_000L
    }
}
