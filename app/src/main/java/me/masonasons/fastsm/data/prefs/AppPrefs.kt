package me.masonasons.fastsm.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.masonasons.fastsm.domain.model.PostAction
import me.masonasons.fastsm.domain.template.CwMode
import me.masonasons.fastsm.domain.template.PostTemplateRenderer

/**
 * Non-secret app preferences (active account id, last-used instance, etc.).
 * Plain SharedPreferences — secrets live in [TokenStore].
 */
class AppPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val activeAccountFlow = MutableStateFlow(
        prefs.getLong(KEY_ACTIVE_ACCOUNT, 0L).takeIf { it != 0L }
    )
    val activeAccountId: StateFlow<Long?> = activeAccountFlow.asStateFlow()

    private val streamingEnabledFlow = MutableStateFlow(
        prefs.getBoolean(KEY_STREAMING, true)
    )
    val streamingEnabled: StateFlow<Boolean> = streamingEnabledFlow.asStateFlow()

    private val markerSyncFlow = MutableStateFlow(
        prefs.getBoolean(KEY_MARKER_SYNC, true)
    )
    val markerSyncEnabled: StateFlow<Boolean> = markerSyncFlow.asStateFlow()

    private val rememberPositionsFlow = MutableStateFlow(
        prefs.getBoolean(KEY_REMEMBER_POSITIONS, false)
    )
    /** When true, every opened timeline (not just home) persists scroll position
     *  across app restarts. For home, it acts as a fallback when marker sync is
     *  off or the server marker couldn't be restored. */
    val rememberTimelinePositions: StateFlow<Boolean> = rememberPositionsFlow.asStateFlow()

    private val fetchPagesFlow = MutableStateFlow(
        prefs.getInt(KEY_FETCH_PAGES, 1).coerceIn(FETCH_PAGES_MIN, FETCH_PAGES_MAX)
    )
    /** Number of back-to-back API calls to make when loading a timeline. 1..10. */
    val fetchPages: StateFlow<Int> = fetchPagesFlow.asStateFlow()

    private val autoFocusComposeFlow = MutableStateFlow(prefs.getBoolean(KEY_AUTO_FOCUS_COMPOSE, true))
    /** When true, the compose text field auto-focuses (showing the keyboard) on open. */
    val autoFocusCompose: StateFlow<Boolean> = autoFocusComposeFlow.asStateFlow()

    private val submitOnImeActionFlow = MutableStateFlow(prefs.getBoolean(KEY_SUBMIT_ON_IME_ACTION, false))
    /** When true, the keyboard's submit/send action (incl. Braille Keyboard's
     *  submit gesture) submits the composed post. Newline characters still
     *  insert newlines — only the explicit IME Send action fires submit. */
    val submitOnImeAction: StateFlow<Boolean> = submitOnImeActionFlow.asStateFlow()

    private val postTemplateFlow = MutableStateFlow(
        prefs.getString(KEY_POST_TEMPLATE, null) ?: PostTemplateRenderer.DEFAULT_POST
    )
    val postTemplate: StateFlow<String> = postTemplateFlow.asStateFlow()

    private val boostTemplateFlow = MutableStateFlow(
        prefs.getString(KEY_BOOST_TEMPLATE, null) ?: PostTemplateRenderer.DEFAULT_BOOST
    )
    val boostTemplate: StateFlow<String> = boostTemplateFlow.asStateFlow()

    private val notificationTemplateFlow = MutableStateFlow(
        prefs.getString(KEY_NOTIFICATION_TEMPLATE, null) ?: PostTemplateRenderer.DEFAULT_NOTIFICATION
    )
    val notificationTemplate: StateFlow<String> = notificationTemplateFlow.asStateFlow()

    private val cwModeFlow = MutableStateFlow(
        CwMode.fromKey(prefs.getString(KEY_CW_MODE, null))
    )
    /** How content warnings are folded into a post's `$text$`. Mirrors desktop. */
    val cwMode: StateFlow<CwMode> = cwModeFlow.asStateFlow()

    private val includeMediaDescriptionsFlow = MutableStateFlow(
        prefs.getBoolean(KEY_INCLUDE_MEDIA_DESCRIPTIONS, true)
    )
    /** When true, media type + alt text is appended to `$text$` during template rendering. */
    val includeMediaDescriptions: StateFlow<Boolean> = includeMediaDescriptionsFlow.asStateFlow()

    private val demojifyDisplayNamesFlow = MutableStateFlow(
        prefs.getBoolean(KEY_DEMOJIFY_DISPLAY_NAMES, false)
    )
    /** When true, strip emoji out of display names before announcing them. */
    val demojifyDisplayNames: StateFlow<Boolean> = demojifyDisplayNamesFlow.asStateFlow()

    private val maxUsernamesDisplayFlow = MutableStateFlow(
        prefs.getInt(KEY_MAX_USERNAMES_DISPLAY, 0).coerceIn(0, MAX_USERNAMES_DISPLAY_MAX)
    )
    /** 0 disables the collapser; otherwise consecutive leading `@handle` tokens
     *  past this threshold are rewritten to `@first and N more ...`. */
    val maxUsernamesDisplay: StateFlow<Int> = maxUsernamesDisplayFlow.asStateFlow()

    private val enabledPostActionsFlow = MutableStateFlow(readEnabledPostActions())
    /** Which custom accessibility actions to expose on a post. Defaults to all. */
    val enabledPostActions: StateFlow<Set<PostAction>> = enabledPostActionsFlow.asStateFlow()

    private fun readEnabledPostActions(): Set<PostAction> {
        // First-run: no stored value, enable everything. After that the stored
        // set is authoritative — an empty stored set means "user disabled all",
        // don't silently re-enable them.
        if (!prefs.contains(KEY_POST_ACTIONS)) return PostAction.ALL
        val raw = prefs.getStringSet(KEY_POST_ACTIONS, emptySet()).orEmpty()
        return raw.mapNotNull { PostAction.fromKey(it) }.toSet()
    }

    fun setActiveAccountId(id: Long?) {
        if (id == null) {
            prefs.edit().remove(KEY_ACTIVE_ACCOUNT).apply()
        } else {
            prefs.edit().putLong(KEY_ACTIVE_ACCOUNT, id).apply()
        }
        activeAccountFlow.value = id
    }

    fun setStreamingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STREAMING, enabled).apply()
        streamingEnabledFlow.value = enabled
    }

    fun setMarkerSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MARKER_SYNC, enabled).apply()
        markerSyncFlow.value = enabled
    }

    fun setRememberTimelinePositions(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMEMBER_POSITIONS, enabled).apply()
        rememberPositionsFlow.value = enabled
    }

    fun setFetchPages(value: Int) {
        val clamped = value.coerceIn(FETCH_PAGES_MIN, FETCH_PAGES_MAX)
        prefs.edit().putInt(KEY_FETCH_PAGES, clamped).apply()
        fetchPagesFlow.value = clamped
    }

    fun setAutoFocusCompose(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_FOCUS_COMPOSE, enabled).apply()
        autoFocusComposeFlow.value = enabled
    }

    fun setSubmitOnImeAction(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SUBMIT_ON_IME_ACTION, enabled).apply()
        submitOnImeActionFlow.value = enabled
    }

    fun setPostTemplate(value: String) {
        prefs.edit().putString(KEY_POST_TEMPLATE, value).apply()
        postTemplateFlow.value = value
    }

    fun setBoostTemplate(value: String) {
        prefs.edit().putString(KEY_BOOST_TEMPLATE, value).apply()
        boostTemplateFlow.value = value
    }

    fun setNotificationTemplate(value: String) {
        prefs.edit().putString(KEY_NOTIFICATION_TEMPLATE, value).apply()
        notificationTemplateFlow.value = value
    }

    fun setCwMode(mode: CwMode) {
        prefs.edit().putString(KEY_CW_MODE, mode.key).apply()
        cwModeFlow.value = mode
    }

    fun setIncludeMediaDescriptions(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INCLUDE_MEDIA_DESCRIPTIONS, enabled).apply()
        includeMediaDescriptionsFlow.value = enabled
    }

    fun setDemojifyDisplayNames(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEMOJIFY_DISPLAY_NAMES, enabled).apply()
        demojifyDisplayNamesFlow.value = enabled
    }

    fun setMaxUsernamesDisplay(value: Int) {
        val clamped = value.coerceIn(0, MAX_USERNAMES_DISPLAY_MAX)
        prefs.edit().putInt(KEY_MAX_USERNAMES_DISPLAY, clamped).apply()
        maxUsernamesDisplayFlow.value = clamped
    }

    fun setPostActionEnabled(action: PostAction, enabled: Boolean) {
        val current = enabledPostActionsFlow.value
        val next = if (enabled) current + action else current - action
        prefs.edit().putStringSet(KEY_POST_ACTIONS, next.map { it.key }.toSet()).apply()
        enabledPostActionsFlow.value = next
    }

    companion object {
        const val FETCH_PAGES_MIN = 1
        const val FETCH_PAGES_MAX = 10
        const val MAX_USERNAMES_DISPLAY_MAX = 20

        private const val FILE_NAME = "fastsm_prefs"
        private const val KEY_ACTIVE_ACCOUNT = "active_account_id"
        private const val KEY_STREAMING = "streaming_enabled"
        private const val KEY_MARKER_SYNC = "marker_sync_enabled"
        private const val KEY_FETCH_PAGES = "fetch_pages"
        private const val KEY_AUTO_FOCUS_COMPOSE = "auto_focus_compose"
        private const val KEY_SUBMIT_ON_IME_ACTION = "submit_on_ime_action"
        private const val KEY_POST_ACTIONS = "enabled_post_actions"
        private const val KEY_POST_TEMPLATE = "post_template"
        private const val KEY_BOOST_TEMPLATE = "boost_template"
        private const val KEY_NOTIFICATION_TEMPLATE = "notification_template"
        private const val KEY_CW_MODE = "cw_mode"
        private const val KEY_INCLUDE_MEDIA_DESCRIPTIONS = "include_media_descriptions"
        private const val KEY_DEMOJIFY_DISPLAY_NAMES = "demojify_display_names"
        private const val KEY_MAX_USERNAMES_DISPLAY = "max_usernames_display"
        private const val KEY_REMEMBER_POSITIONS = "remember_timeline_positions"
    }
}
