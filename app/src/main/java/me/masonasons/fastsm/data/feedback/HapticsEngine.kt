package me.masonasons.fastsm.data.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import me.masonasons.fastsm.domain.model.AppEvent

/**
 * Short-buzz haptic feedback for a handful of "something notable happened"
 * events. Masters + per-event toggles come from [FeedbackPrefs].
 *
 * Durations are deliberately short — Android's vibrator on most phones reads
 * ~40ms as a crisp click, ~100ms as a clear acknowledgement, ~200ms as a
 * warning. Longer than that starts to feel disruptive.
 */
class HapticsEngine(
    context: Context,
    private val prefs: FeedbackPrefs,
) {

    private val vibrator: Vibrator? = run {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            mgr?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun handle(event: AppEvent) {
        if (!prefs.hapticsEnabled.value) return
        val (durationMs, amplitude) = pattern(event) ?: return
        vibrate(durationMs, amplitude)
    }

    private fun pattern(event: AppEvent): Pair<Long, Int>? = when (event) {
        AppEvent.PostSent, AppEvent.ReplySent, AppEvent.BoostSent ->
            if (prefs.hapticsPostSent.value) 100L to 180 else null
        is AppEvent.NewPostReceived ->
            if (prefs.hapticsNewPost.value) 40L to 120 else null
        is AppEvent.NotificationReceived ->
            if (prefs.hapticsNotification.value) 100L to 200 else null
        is AppEvent.PostFailed, is AppEvent.Error ->
            if (prefs.hapticsError.value) 200L to 255 else null
        else -> null
    }

    private fun vibrate(durationMs: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = VibrationEffect.createOneShot(durationMs, amplitude)
        v.vibrate(effect)
    }
}
