package me.masonasons.fastsm.data.notify

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Per-feature prefs for background notification polling.
 *
 * `enabled` toggles the whole feature. `lastSeenIds` tracks, per account, the
 * id of the most-recent notification we've surfaced as an Android notification —
 * on the next poll we only push new items whose id is newer than this marker.
 */
class NotificationPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val enabledFlow = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = enabledFlow.asStateFlow()

    fun setEnabled(v: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, v).apply()
        enabledFlow.value = v
    }

    fun lastSeenId(accountId: Long): String? =
        prefs.getString("$KEY_LAST_SEEN_PREFIX$accountId", null)

    fun setLastSeenId(accountId: Long, id: String) {
        prefs.edit().putString("$KEY_LAST_SEEN_PREFIX$accountId", id).apply()
    }

    private companion object {
        const val FILE = "fastsm_notifications"
        const val KEY_ENABLED = "enabled"
        const val KEY_LAST_SEEN_PREFIX = "last_seen_"
    }
}
