package me.masonasons.fastsm.platform

import io.ktor.client.HttpClient
import me.masonasons.fastsm.data.repo.AccountRepository
import me.masonasons.fastsm.domain.model.Account
import me.masonasons.fastsm.domain.model.PlatformType
import me.masonasons.fastsm.platform.bluesky.BlueskyAuthCoordinator
import me.masonasons.fastsm.platform.bluesky.BlueskyPlatform
import me.masonasons.fastsm.platform.mastodon.MastodonPlatform

/**
 * Builds the right [PlatformAccount] impl for an [Account]. One instance per
 * app — caches the `account.id -> PlatformAccount` mapping so we reuse the
 * same Ktor-backed object across view models.
 */
class PlatformFactory(
    private val httpClient: HttpClient,
    private val accountRepository: AccountRepository,
    private val blueskyAuthCoordinator: BlueskyAuthCoordinator,
) {

    private val cache = mutableMapOf<Long, PlatformAccount>()
    private val lock = Any()

    fun forAccount(account: Account): PlatformAccount = synchronized(lock) {
        cache.getOrPut(account.id) { build(account) }
    }

    fun invalidate(accountId: Long) = synchronized(lock) { cache.remove(accountId) }

    private fun build(account: Account): PlatformAccount = when (account.platform) {
        PlatformType.MASTODON -> MastodonPlatform(
            account = account,
            httpClient = httpClient,
            tokenProvider = { accountRepository.tokenFor(account.id) },
        )
        PlatformType.BLUESKY -> BlueskyPlatform(
            account = account,
            httpClient = httpClient,
            accessJwtProvider = { accountRepository.tokenFor(account.id) },
            authCoordinator = blueskyAuthCoordinator,
        )
    }
}
