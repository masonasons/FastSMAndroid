package me.masonasons.fastsm.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.masonasons.fastsm.data.db.dao.AccountDao
import me.masonasons.fastsm.data.db.entity.AccountEntity
import me.masonasons.fastsm.data.prefs.AppPrefs
import me.masonasons.fastsm.data.prefs.TokenStore
import me.masonasons.fastsm.domain.model.Account
import me.masonasons.fastsm.domain.model.PlatformType

class AccountRepository(
    private val accountDao: AccountDao,
    private val tokenStore: TokenStore,
    private val appPrefs: AppPrefs,
) {

    val accounts: Flow<List<Account>> =
        accountDao.observeAll().map { list -> list.map { it.toDomain() } }

    val activeAccountId = appPrefs.activeAccountId

    suspend fun getAll(): List<Account> = accountDao.getAll().map { it.toDomain() }

    suspend fun getById(id: Long): Account? = accountDao.getById(id)?.toDomain()

    suspend fun getActiveAccount(): Account? {
        val id = appPrefs.activeAccountId.value ?: return getAll().firstOrNull()
        return getById(id) ?: getAll().firstOrNull()
    }

    /**
     * Persist a newly authenticated Mastodon account (and stash client creds
     * for future token refreshes). Returns the allocated account id.
     */
    suspend fun saveBlueskyAccount(
        pdsBase: String,
        did: String,
        handle: String,
        displayName: String,
        avatar: String?,
        accessJwt: String,
        refreshJwt: String,
    ): Long {
        val entity = AccountEntity(
            platform = PlatformType.BLUESKY.wire,
            instance = pdsBase,
            userId = did,
            acct = handle,
            displayName = displayName,
            avatar = avatar,
            clientId = null,
            clientSecret = null,
            createdAt = System.currentTimeMillis(),
        )
        val id = accountDao.upsert(entity)
        tokenStore.setAccessToken(id, accessJwt)
        tokenStore.setRefreshToken(id, refreshJwt)
        if (appPrefs.activeAccountId.value == null) appPrefs.setActiveAccountId(id)
        return id
    }

    suspend fun saveMastodonAccount(
        instance: String,
        userId: String,
        acct: String,
        displayName: String,
        avatar: String?,
        accessToken: String,
        clientId: String,
        clientSecret: String,
    ): Long {
        val entity = AccountEntity(
            platform = PlatformType.MASTODON.wire,
            instance = instance,
            userId = userId,
            acct = acct,
            displayName = displayName,
            avatar = avatar,
            clientId = clientId,
            clientSecret = clientSecret,
            createdAt = System.currentTimeMillis(),
        )
        val id = accountDao.upsert(entity)
        tokenStore.setAccessToken(id, accessToken)
        if (appPrefs.activeAccountId.value == null) appPrefs.setActiveAccountId(id)
        return id
    }

    suspend fun delete(id: Long) {
        accountDao.deleteById(id)
        tokenStore.clearAccessToken(id)
        if (appPrefs.activeAccountId.value == id) {
            val fallback = accountDao.getAll().firstOrNull()?.id
            appPrefs.setActiveAccountId(fallback)
        }
    }

    fun setActive(id: Long) = appPrefs.setActiveAccountId(id)

    fun tokenFor(id: Long): String? = tokenStore.getAccessToken(id)

    private fun AccountEntity.toDomain(): Account = Account(
        id = id,
        platform = PlatformType.fromWire(platform),
        instance = instance,
        userId = userId,
        acct = acct,
        displayName = displayName,
        avatar = avatar,
    )
}
