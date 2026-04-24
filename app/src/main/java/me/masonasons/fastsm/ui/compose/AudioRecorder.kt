package me.masonasons.fastsm.ui.compose

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Thin wrapper over [MediaRecorder] that writes raw AAC (ADTS) to a temp file.
 *
 * Why not MP4/.m4a: Mastodon's uploader probes the container with ffprobe to
 * classify media as image/audio/video. An audio-only MP4 can get misclassified
 * as video by some instance configs — uploads succeed but the post fails with
 * "video has no video stream". Raw AAC is unambiguous — the container itself
 * carries no video hooks, the extension is `.aac`, and the MIME is `audio/aac`.
 *
 * This is single-shot: construct, start, stop — then build a fresh instance
 * for the next recording. MediaRecorder's state machine punishes reuse.
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    val isRecording: Boolean get() = recorder != null

    fun start(): File {
        check(recorder == null) { "Already recording" }
        val file = File.createTempFile("fastsm-audio-", ".aac", context.cacheDir)
        val rec = buildRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            // 128 kbps is overkill for voice but still tiny compared to Mastodon's
            // per-attachment limits; keeps the recording crisp.
            setAudioChannels(2)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = rec
        outputFile = file
        return file
    }

    /** Stop recording and return the finished file (or null if nothing was started). */
    fun stop(): File? {
        val rec = recorder ?: return null
        val file = outputFile
        try {
            rec.stop()
        } catch (_: RuntimeException) {
            // MediaRecorder throws if stop() fires before any meaningful data was
            // captured (tap record + immediate stop). The file is still there,
            // just empty — caller decides whether to upload.
        }
        rec.release()
        recorder = null
        outputFile = null
        return file
    }

    /** Abandon the in-progress recording without producing a usable file. */
    fun cancel() {
        val rec = recorder ?: return
        runCatching { rec.stop() }
        rec.release()
        recorder = null
        outputFile?.delete()
        outputFile = null
    }

    private fun buildRecorder(): MediaRecorder =
        // The no-arg constructor was deprecated in Android 12; the context
        // variant is the only one the lint checker's happy with there.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
        else @Suppress("DEPRECATION") MediaRecorder()
}
