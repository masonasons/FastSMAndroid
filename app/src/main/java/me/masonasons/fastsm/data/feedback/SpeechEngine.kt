package me.masonasons.fastsm.data.feedback

import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import me.masonasons.fastsm.domain.model.AppEvent

/**
 * Sends short announcements to the active accessibility service (TalkBack)
 * via [AccessibilityManager.sendAccessibilityEvent]. The speech engine holds
 * no TextToSpeech instance of its own — the user's TalkBack voice, rate,
 * pitch, and interruption model win.
 *
 * If no accessibility service is running, announcements are dropped silently.
 * That's the correct behavior: the app is accessibility-first, and a user
 * without a screen reader doesn't want the phone speaking at them.
 */
class SpeechEngine(
    private val context: Context,
    private val prefs: FeedbackPrefs,
) {

    private val am: AccessibilityManager =
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

    fun handle(event: AppEvent) {
        if (!prefs.speechEnabled.value) return
        if (!am.isEnabled) return
        val text = announcement(event) ?: return
        announce(text)
    }

    private fun announce(text: String) {
        val e = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            AccessibilityEvent()
        } else {
            @Suppress("DEPRECATION")
            AccessibilityEvent.obtain()
        }
        e.eventType = AccessibilityEvent.TYPE_ANNOUNCEMENT
        e.className = javaClass.name
        e.packageName = context.packageName
        e.text.add(text)
        am.sendAccessibilityEvent(e)
    }

    private fun announcement(event: AppEvent): String? = when (event) {
        is AppEvent.TabLoaded ->
            if (prefs.speakTabLoaded.value) {
                if (event.count == 0) "No posts" else "${event.count} posts loaded"
            } else null
        is AppEvent.NotificationReceived ->
            if (prefs.speakNotification.value) event.text else null
        AppEvent.PostSent ->
            if (prefs.speakPostSent.value) "Post sent" else null
        AppEvent.ReplySent ->
            if (prefs.speakPostSent.value) "Reply sent" else null
        AppEvent.BoostSent ->
            if (prefs.speakPostSent.value) "Boosted" else null
        is AppEvent.PostFailed ->
            if (prefs.speakError.value) "Post failed: ${event.message}" else null
        is AppEvent.Error ->
            if (prefs.speakError.value) event.message else null
        // Silent events — UI already gives feedback on toggles / interaction.
        is AppEvent.NewPostReceived,
        AppEvent.Favourited,
        AppEvent.Unfavourited,
        AppEvent.Bookmarked,
        AppEvent.Followed,
        AppEvent.Unfollowed,
        AppEvent.Deleted -> null
    }
}
