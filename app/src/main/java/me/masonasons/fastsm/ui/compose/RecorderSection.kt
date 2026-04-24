package me.masonasons.fastsm.ui.compose

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

/**
 * Record-audio controls. Idle state: "Record audio" button. Recording state:
 * MM:SS timer with Stop + Cancel buttons. On stop, [onFinish] receives the
 * raw file bytes + filename + MIME, ready to feed into the media upload path.
 */
@Composable
fun RecorderSection(
    enabled: Boolean,
    onFinish: (bytes: ByteArray, filename: String, mime: String) -> Unit,
) {
    val context = LocalContext.current
    val recorder = remember { AudioRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var elapsedSec by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    // Ensure we don't leak a running MediaRecorder if the composable leaves
    // the tree while recording (e.g. back navigation).
    DisposableEffect(Unit) {
        onDispose { recorder.cancel() }
    }

    // Tick the elapsed counter once per second while recording is active.
    LaunchedEffect(recording) {
        if (!recording) return@LaunchedEffect
        elapsedSec = 0
        while (recording) {
            delay(1_000)
            elapsedSec += 1
        }
    }

    fun startRecording() {
        error = null
        runCatching { recorder.start() }
            .onSuccess { recording = true }
            .onFailure { e -> error = "Couldn't start: ${e.message ?: "unknown error"}" }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording()
        else error = "Microphone permission denied"
    }

    if (!recording) {
        OutlinedButton(
            onClick = {
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) startRecording()
                else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            enabled = enabled,
        ) {
            Icon(Icons.Filled.Mic, contentDescription = null)
            Text("  Record audio")
        }
        error?.let { e ->
            Text(e, color = MaterialTheme.colorScheme.error)
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Recording \u00b7 ${formatElapsed(elapsedSec)}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                val file = recorder.stop()
                recording = false
                if (file != null && file.length() > 0) {
                    val bytes = runCatching { file.readBytes() }.getOrNull()
                    file.delete()
                    if (bytes != null) onFinish(bytes, "voice-${System.currentTimeMillis()}.aac", "audio/aac")
                    else error = "Recording was empty"
                } else {
                    error = "Recording was too short"
                }
            }) {
                Icon(Icons.Filled.Stop, contentDescription = "Stop and attach recording")
            }
            IconButton(onClick = {
                recorder.cancel()
                recording = false
            }) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel recording")
            }
        }
    }
}

private fun formatElapsed(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
