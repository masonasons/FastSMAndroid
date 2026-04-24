package me.masonasons.fastsm.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * OAuth access tokens keyed by account id. Encrypted at rest via
 * EncryptedSharedPreferences. Survives reinstalls if cloud backup is on,
 * though in practice the backup rules exclude it.
 */
class TokenStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getAccessToken(accountId: Long): String? =
        prefs.getString(tokenKey(accountId), null)

    fun setAccessToken(accountId: Long, token: String) {
        prefs.edit().putString(tokenKey(accountId), token).apply()
    }

    fun getRefreshToken(accountId: Long): String? =
        prefs.getString(refreshKey(accountId), null)

    fun setRefreshToken(accountId: Long, token: String) {
        prefs.edit().putString(refreshKey(accountId), token).apply()
    }

    fun clearAccessToken(accountId: Long) {
        prefs.edit().remove(tokenKey(accountId)).remove(refreshKey(accountId)).apply()
    }

    private fun tokenKey(accountId: Long) = "token_$accountId"
    private fun refreshKey(accountId: Long) = "refresh_$accountId"

    private companion object {
        const val FILE_NAME = "fastsm_secure"
    }
}
