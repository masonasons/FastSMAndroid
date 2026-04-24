package me.masonasons.fastsm.data.feedback

import me.masonasons.fastsm.domain.model.AppEvent

/**
 * Entry point the rest of the app uses to announce things. Fans out to
 * [SpeechEngine] (TTS), [SoundEngine] (soundpack .ogg), and [HapticsEngine]
 * (vibration). Each subsystem decides internally whether it cares about the
 * event, so call sites stay unaware of which feedback channel is active.
 *
 * [specId] scopes sound playback — if the user muted a particular timeline's
 * sounds, we skip the sound channel but still run speech/haptics. TalkBack
 * users may want ambient sounds off on a noisy tab without losing the
 * "post sent" confirmation speech.
 */
class FeedbackManager(
    val prefs: FeedbackPrefs,
    private val speech: SpeechEngine,
    private val sound: SoundEngine,
    private val haptics: HapticsEngine,
) {
    fun emit(event: AppEvent, specId: String? = null) {
        speech.handle(event)
        sound.handle(event, specId)
        haptics.handle(event)
    }

    fun toggleMuted(specId: String) = prefs.toggleMuted(specId)
    fun isMuted(specId: String): Boolean = specId in prefs.mutedSpecs.value
    fun availablePacks(): List<String> = sound.availablePacks()
}
