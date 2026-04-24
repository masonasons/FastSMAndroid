package me.masonasons.fastsm.data.feedback

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings that govern the feedback subsystems (speech / sound / haptics).
 * Each master toggle and per-event toggle is persisted in its own dedicated
 * SharedPreferences file so the main AppPrefs stays compact.
 */
class FeedbackPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    // --- Speech ---
    private val speechMasterFlow = MutableStateFlow(prefs.getBoolean(KEY_SPEECH, true))
    val speechEnabled: StateFlow<Boolean> = speechMasterFlow.asStateFlow()

    private val speechTabLoadedFlow = MutableStateFlow(prefs.getBoolean(KEY_SPEECH_TAB_LOADED, true))
    val speakTabLoaded: StateFlow<Boolean> = speechTabLoadedFlow.asStateFlow()

    private val speechNotificationFlow = MutableStateFlow(prefs.getBoolean(KEY_SPEECH_NOTIFICATION, true))
    val speakNotification: StateFlow<Boolean> = speechNotificationFlow.asStateFlow()

    private val speechPostSentFlow = MutableStateFlow(prefs.getBoolean(KEY_SPEECH_POST_SENT, false))
    val speakPostSent: StateFlow<Boolean> = speechPostSentFlow.asStateFlow()

    private val speechErrorFlow = MutableStateFlow(prefs.getBoolean(KEY_SPEECH_ERROR, true))
    val speakError: StateFlow<Boolean> = speechErrorFlow.asStateFlow()

    // --- Sound ---
    private val soundMasterFlow = MutableStateFlow(prefs.getBoolean(KEY_SOUND, true))
    val soundEnabled: StateFlow<Boolean> = soundMasterFlow.asStateFlow()

    // Per-account soundpack map. Stored as a plain string set of
    // "accountId=packName" pairs — simpler than adding a JSON dependency just
    // for this one value. Missing accounts fall back to "default".
    private val accountSoundpacksFlow = MutableStateFlow(readAccountSoundpacks())
    val accountSoundpacks: StateFlow<Map<Long, String>> = accountSoundpacksFlow.asStateFlow()

    private fun readAccountSoundpacks(): Map<Long, String> {
        val raw = prefs.getStringSet(KEY_ACCOUNT_SOUNDPACKS, emptySet()).orEmpty()
        return raw.mapNotNull { entry ->
            val eq = entry.indexOf('=')
            if (eq <= 0) return@mapNotNull null
            val id = entry.substring(0, eq).toLongOrNull() ?: return@mapNotNull null
            val pack = entry.substring(eq + 1)
            id to pack
        }.toMap()
    }

    /** Resolve the soundpack name for [accountId]. "default" when unmapped. */
    fun soundpackFor(accountId: Long?): String {
        if (accountId == null) return "default"
        return accountSoundpacksFlow.value[accountId] ?: "default"
    }

    private val soundVolumeFlow = MutableStateFlow(prefs.getFloat(KEY_SOUND_VOLUME, 1.0f))
    val soundVolume: StateFlow<Float> = soundVolumeFlow.asStateFlow()

    private val mutedSpecsFlow = MutableStateFlow(
        prefs.getStringSet(KEY_MUTED_SPECS, emptySet()).orEmpty().toSet()
    )
    /** Spec IDs whose sound effects are muted. Empty means "nothing muted". */
    val mutedSpecs: StateFlow<Set<String>> = mutedSpecsFlow.asStateFlow()

    // --- Haptics ---
    private val hapticsMasterFlow = MutableStateFlow(prefs.getBoolean(KEY_HAPTICS, true))
    val hapticsEnabled: StateFlow<Boolean> = hapticsMasterFlow.asStateFlow()

    private val hapticsPostSentFlow = MutableStateFlow(prefs.getBoolean(KEY_HAPTICS_POST_SENT, true))
    val hapticsPostSent: StateFlow<Boolean> = hapticsPostSentFlow.asStateFlow()

    private val hapticsNewPostFlow = MutableStateFlow(prefs.getBoolean(KEY_HAPTICS_NEW_POST, false))
    val hapticsNewPost: StateFlow<Boolean> = hapticsNewPostFlow.asStateFlow()

    private val hapticsNotificationFlow = MutableStateFlow(prefs.getBoolean(KEY_HAPTICS_NOTIFICATION, true))
    val hapticsNotification: StateFlow<Boolean> = hapticsNotificationFlow.asStateFlow()

    private val hapticsErrorFlow = MutableStateFlow(prefs.getBoolean(KEY_HAPTICS_ERROR, true))
    val hapticsError: StateFlow<Boolean> = hapticsErrorFlow.asStateFlow()

    // --- Setters ---
    fun setSpeechEnabled(v: Boolean) { prefs.edit().putBoolean(KEY_SPEECH, v).apply(); speechMasterFlow.value = v }
    fun setSpeakTabLoaded(v: Boolean) { prefs.edit().putBoolean(KEY_SPEECH_TAB_LOADED, v).apply(); speechTabLoadedFlow.value = v }
    fun setSpeakNotification(v: Boolean) { prefs.edit().putBoolean(KEY_SPEECH_NOTIFICATION, v).apply(); speechNotificationFlow.value = v }
    fun setSpeakPostSent(v: Boolean) { prefs.edit().putBoolean(KEY_SPEECH_POST_SENT, v).apply(); speechPostSentFlow.value = v }
    fun setSpeakError(v: Boolean) { prefs.edit().putBoolean(KEY_SPEECH_ERROR, v).apply(); speechErrorFlow.value = v }

    fun setSoundEnabled(v: Boolean) { prefs.edit().putBoolean(KEY_SOUND, v).apply(); soundMasterFlow.value = v }
    fun setAccountSoundpack(accountId: Long, pack: String) {
        val next = accountSoundpacksFlow.value.toMutableMap().apply { put(accountId, pack) }
        prefs.edit()
            .putStringSet(KEY_ACCOUNT_SOUNDPACKS, next.map { (id, p) -> "$id=$p" }.toSet())
            .apply()
        accountSoundpacksFlow.value = next
    }
    fun setSoundVolume(v: Float) { prefs.edit().putFloat(KEY_SOUND_VOLUME, v.coerceIn(0f, 1f)).apply(); soundVolumeFlow.value = v.coerceIn(0f, 1f) }

    fun toggleMuted(specId: String) {
        val current = mutedSpecsFlow.value
        val next = if (specId in current) current - specId else current + specId
        prefs.edit().putStringSet(KEY_MUTED_SPECS, next).apply()
        mutedSpecsFlow.value = next
    }

    fun setHapticsEnabled(v: Boolean) { prefs.edit().putBoolean(KEY_HAPTICS, v).apply(); hapticsMasterFlow.value = v }
    fun setHapticsPostSent(v: Boolean) { prefs.edit().putBoolean(KEY_HAPTICS_POST_SENT, v).apply(); hapticsPostSentFlow.value = v }
    fun setHapticsNewPost(v: Boolean) { prefs.edit().putBoolean(KEY_HAPTICS_NEW_POST, v).apply(); hapticsNewPostFlow.value = v }
    fun setHapticsNotification(v: Boolean) { prefs.edit().putBoolean(KEY_HAPTICS_NOTIFICATION, v).apply(); hapticsNotificationFlow.value = v }
    fun setHapticsError(v: Boolean) { prefs.edit().putBoolean(KEY_HAPTICS_ERROR, v).apply(); hapticsErrorFlow.value = v }

    private companion object {
        const val FILE_NAME = "fastsm_feedback"
        const val KEY_SPEECH = "speech_enabled"
        const val KEY_SPEECH_TAB_LOADED = "speech_tab_loaded"
        const val KEY_SPEECH_NOTIFICATION = "speech_notification"
        const val KEY_SPEECH_POST_SENT = "speech_post_sent"
        const val KEY_SPEECH_ERROR = "speech_error"
        const val KEY_SOUND = "sound_enabled"
        const val KEY_ACCOUNT_SOUNDPACKS = "account_soundpacks"
        const val KEY_SOUND_VOLUME = "sound_volume"
        const val KEY_MUTED_SPECS = "muted_specs"
        const val KEY_HAPTICS = "haptics_enabled"
        const val KEY_HAPTICS_POST_SENT = "haptics_post_sent"
        const val KEY_HAPTICS_NEW_POST = "haptics_new_post"
        const val KEY_HAPTICS_NOTIFICATION = "haptics_notification"
        const val KEY_HAPTICS_ERROR = "haptics_error"
    }
}
