package me.masonasons.fastsm.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.masonasons.fastsm.data.feedback.FeedbackManager
import me.masonasons.fastsm.data.feedback.FeedbackPrefs
import me.masonasons.fastsm.data.prefs.AppPrefs
import me.masonasons.fastsm.domain.model.PostAction
import me.masonasons.fastsm.domain.template.CwMode
import me.masonasons.fastsm.util.AppUpdater

class SettingsViewModel(
    private val appPrefs: AppPrefs,
    private val feedbackPrefs: FeedbackPrefs,
    private val feedback: FeedbackManager,
    private val notificationPrefs: me.masonasons.fastsm.data.notify.NotificationPrefs,
    private val appUpdater: AppUpdater,
) : ViewModel() {

    sealed interface UpdateUiState {
        data object Idle : UpdateUiState
        data object Checking : UpdateUiState
        data object UpToDate : UpdateUiState
        data class Available(val remoteVersion: String, val installedVersion: String) : UpdateUiState
        data object Downloading : UpdateUiState
        data class Error(val message: String) : UpdateUiState
    }

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    fun checkForUpdate() {
        if (_updateState.value is UpdateUiState.Checking || _updateState.value is UpdateUiState.Downloading) return
        _updateState.value = UpdateUiState.Checking
        viewModelScope.launch {
            _updateState.value = when (val r = appUpdater.check()) {
                AppUpdater.Check.UpToDate -> UpdateUiState.UpToDate
                is AppUpdater.Check.Available -> UpdateUiState.Available(r.remoteVersion, r.installedVersion)
                is AppUpdater.Check.Failed -> UpdateUiState.Error(r.reason)
            }
        }
    }

    fun downloadAndInstall() {
        if (_updateState.value is UpdateUiState.Downloading) return
        _updateState.value = UpdateUiState.Downloading
        viewModelScope.launch {
            val err = appUpdater.downloadAndInstall()
            _updateState.value = if (err == null) UpdateUiState.Idle else UpdateUiState.Error(err)
        }
    }

    val backgroundNotifications: StateFlow<Boolean> = notificationPrefs.enabled
    fun setBackgroundNotifications(v: Boolean) = notificationPrefs.setEnabled(v)

    val streamingEnabled: StateFlow<Boolean> = appPrefs.streamingEnabled
    val markerSyncEnabled: StateFlow<Boolean> = appPrefs.markerSyncEnabled
    val rememberTimelinePositions: StateFlow<Boolean> = appPrefs.rememberTimelinePositions
    val fetchPages: StateFlow<Int> = appPrefs.fetchPages
    val autoFocusCompose: StateFlow<Boolean> = appPrefs.autoFocusCompose
    val submitOnImeAction: StateFlow<Boolean> = appPrefs.submitOnImeAction
    val enabledPostActions: StateFlow<Set<PostAction>> = appPrefs.enabledPostActions
    val postTemplate: StateFlow<String> = appPrefs.postTemplate
    val boostTemplate: StateFlow<String> = appPrefs.boostTemplate
    val notificationTemplate: StateFlow<String> = appPrefs.notificationTemplate

    val fetchPagesMin: Int = AppPrefs.FETCH_PAGES_MIN
    val fetchPagesMax: Int = AppPrefs.FETCH_PAGES_MAX
    val maxUsernamesDisplayMax: Int = AppPrefs.MAX_USERNAMES_DISPLAY_MAX

    // Desktop-parity template post-processing
    val cwMode: StateFlow<CwMode> = appPrefs.cwMode
    val includeMediaDescriptions: StateFlow<Boolean> = appPrefs.includeMediaDescriptions
    val demojifyDisplayNames: StateFlow<Boolean> = appPrefs.demojifyDisplayNames
    val maxUsernamesDisplay: StateFlow<Int> = appPrefs.maxUsernamesDisplay

    // Speech
    val speechEnabled: StateFlow<Boolean> = feedbackPrefs.speechEnabled
    val speakTabLoaded: StateFlow<Boolean> = feedbackPrefs.speakTabLoaded
    val speakNotification: StateFlow<Boolean> = feedbackPrefs.speakNotification
    val speakPostSent: StateFlow<Boolean> = feedbackPrefs.speakPostSent
    val speakError: StateFlow<Boolean> = feedbackPrefs.speakError

    // Sound
    val soundEnabled: StateFlow<Boolean> = feedbackPrefs.soundEnabled
    val soundVolume: StateFlow<Float> = feedbackPrefs.soundVolume

    // Haptics
    val hapticsEnabled: StateFlow<Boolean> = feedbackPrefs.hapticsEnabled
    val hapticsPostSent: StateFlow<Boolean> = feedbackPrefs.hapticsPostSent
    val hapticsNewPost: StateFlow<Boolean> = feedbackPrefs.hapticsNewPost
    val hapticsNotification: StateFlow<Boolean> = feedbackPrefs.hapticsNotification
    val hapticsError: StateFlow<Boolean> = feedbackPrefs.hapticsError

    fun setStreamingEnabled(enabled: Boolean) = appPrefs.setStreamingEnabled(enabled)
    fun setMarkerSyncEnabled(enabled: Boolean) = appPrefs.setMarkerSyncEnabled(enabled)
    fun setRememberTimelinePositions(enabled: Boolean) = appPrefs.setRememberTimelinePositions(enabled)
    fun setFetchPages(value: Int) = appPrefs.setFetchPages(value)
    fun setAutoFocusCompose(v: Boolean) = appPrefs.setAutoFocusCompose(v)
    fun setSubmitOnImeAction(v: Boolean) = appPrefs.setSubmitOnImeAction(v)
    fun setPostActionEnabled(action: PostAction, enabled: Boolean) =
        appPrefs.setPostActionEnabled(action, enabled)

    fun setPostTemplate(v: String) = appPrefs.setPostTemplate(v)
    fun setBoostTemplate(v: String) = appPrefs.setBoostTemplate(v)
    fun setNotificationTemplate(v: String) = appPrefs.setNotificationTemplate(v)

    fun setCwMode(v: CwMode) = appPrefs.setCwMode(v)
    fun setIncludeMediaDescriptions(v: Boolean) = appPrefs.setIncludeMediaDescriptions(v)
    fun setDemojifyDisplayNames(v: Boolean) = appPrefs.setDemojifyDisplayNames(v)
    fun setMaxUsernamesDisplay(v: Int) = appPrefs.setMaxUsernamesDisplay(v)

    fun setSpeechEnabled(v: Boolean) = feedbackPrefs.setSpeechEnabled(v)
    fun setSpeakTabLoaded(v: Boolean) = feedbackPrefs.setSpeakTabLoaded(v)
    fun setSpeakNotification(v: Boolean) = feedbackPrefs.setSpeakNotification(v)
    fun setSpeakPostSent(v: Boolean) = feedbackPrefs.setSpeakPostSent(v)
    fun setSpeakError(v: Boolean) = feedbackPrefs.setSpeakError(v)

    fun setSoundEnabled(v: Boolean) = feedbackPrefs.setSoundEnabled(v)
    fun setSoundVolume(v: Float) = feedbackPrefs.setSoundVolume(v)
    fun availableSoundpacks(): List<String> = feedback.availablePacks()

    fun setHapticsEnabled(v: Boolean) = feedbackPrefs.setHapticsEnabled(v)
    fun setHapticsPostSent(v: Boolean) = feedbackPrefs.setHapticsPostSent(v)
    fun setHapticsNewPost(v: Boolean) = feedbackPrefs.setHapticsNewPost(v)
    fun setHapticsNotification(v: Boolean) = feedbackPrefs.setHapticsNotification(v)
    fun setHapticsError(v: Boolean) = feedbackPrefs.setHapticsError(v)
}
