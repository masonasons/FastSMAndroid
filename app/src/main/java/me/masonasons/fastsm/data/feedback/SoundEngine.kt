package me.masonasons.fastsm.data.feedback

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.masonasons.fastsm.data.repo.AccountRepository
import me.masonasons.fastsm.domain.model.AppEvent
import java.io.File

/**
 * Plays short .ogg clips for [AppEvent]s from a user-selectable soundpack.
 *
 * Packs live under `<externalFilesDir>/sounds/<packname>/<event_key>.ogg`.
 * A missing file is silent — the engine never throws; we fall back gracefully
 * so an incomplete pack doesn't break the app.
 *
 * Each playback uses a one-shot [MediaPlayer] that releases on completion.
 * This is simpler than SoundPool (no pre-loading, no pool exhaustion) and
 * sound files this small load nearly instantly.
 */
class SoundEngine(
    private val context: Context,
    private val prefs: FeedbackPrefs,
    private val accountRepository: AccountRepository,
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    /** Whether the given spec is currently muted for sound playback. */
    fun isMuted(specId: String?): Boolean =
        specId != null && specId in prefs.mutedSpecs.value

    private sealed interface SoundSource {
        data class External(val file: File) : SoundSource
        data class Asset(val path: String) : SoundSource
    }

    fun handle(event: AppEvent, specId: String? = null) {
        if (!prefs.soundEnabled.value) return
        if (isMuted(specId)) return
        val source = resolveSound(event.key) ?: return
        val volume = prefs.soundVolume.value
        scope.launch { playOnce(source, volume) }
    }

    private fun playOnce(source: SoundSource, volume: Float) {
        runCatching {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setVolume(volume, volume)
                setOnCompletionListener { runCatching { it.release() } }
                setOnErrorListener { p, _, _ -> runCatching { p.release() }; true }
            }
            // Asset sounds load via the AssetFileDescriptor path — we close the
            // descriptor right after wiring it up, MediaPlayer keeps its own ref.
            when (source) {
                is SoundSource.External -> mp.setDataSource(source.file.absolutePath)
                is SoundSource.Asset -> {
                    var afd: AssetFileDescriptor? = null
                    try {
                        afd = context.assets.openFd(source.path)
                        mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    } finally {
                        afd?.close()
                    }
                }
            }
            mp.prepare()
            mp.start()
        }.onFailure { e ->
            Log.w(TAG, "Couldn't play $source: ${e.message}")
        }
    }

    /**
     * Look up the sound for [eventKey]:
     *  1. User-selected pack under externalFilesDir/sounds/<pack>/<key>.ogg
     *  2. If the pack is "default" or the file is missing, fall back to the
     *     bundled default pack in assets (so a fresh install already has sounds)
     *  3. If the selected pack is custom, also try externalFilesDir/sounds/default
     *     before giving up — a custom pack can intentionally omit events it
     *     doesn't want to override.
     */
    private fun resolveSound(eventKey: String): SoundSource? {
        val root = context.getExternalFilesDir("sounds")
        val pack = prefs.soundpackFor(accountRepository.activeAccountId.value)
        if (root != null) {
            val primary = File(root, "$pack/$eventKey.ogg")
            if (primary.exists()) return SoundSource.External(primary)
            if (pack != "default") {
                val customDefault = File(root, "default/$eventKey.ogg")
                if (customDefault.exists()) return SoundSource.External(customDefault)
            }
        }
        // Bundled fallback. Every fresh install sees this.
        val assetPath = "sounds/default/$eventKey.ogg"
        if (assetExists(assetPath)) return SoundSource.Asset(assetPath)
        return null
    }

    private fun assetExists(path: String): Boolean = runCatching {
        context.assets.openFd(path).close()
        true
    }.getOrDefault(false)

    /** The directory where users should place their soundpack folders. */
    fun soundsRoot(): File? = context.getExternalFilesDir("sounds")

    /** Soundpack folders currently present under [soundsRoot]. */
    fun availablePacks(): List<String> {
        val root = soundsRoot() ?: return listOf("default")
        val packs = root.listFiles { f -> f.isDirectory }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()
        // Always include "default" so the setting dropdown is never empty.
        return if ("default" in packs) packs else listOf("default") + packs
    }

    private companion object {
        const val TAG = "FastSM-Sound"
    }
}
