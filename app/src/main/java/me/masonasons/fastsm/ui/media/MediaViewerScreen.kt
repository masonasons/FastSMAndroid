package me.masonasons.fastsm.ui.media

import android.view.ViewGroup
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive

/**
 * Unified media viewer. Dispatches by [type]:
 *  - "image" / "gifv" → pinch-zoom image viewer
 *  - "video" → Media3 PlayerView
 *  - "audio" → compact Compose player UI
 *
 * ExoPlayer is lifecycle-aware: paused on STOP, released on dispose, so
 * background playback doesn't happen unless we wire a MediaSession later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    url: String,
    type: String?,
    description: String?,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleFor(type)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (type) {
                "video" -> VideoPlayer(url = url, description = description)
                "audio" -> AudioPlayer(url = url, description = description)
                else -> ImageViewer(url = url, description = description)
            }
        }
    }
}

private fun titleFor(type: String?): String = when (type) {
    "video" -> "Video"
    "audio" -> "Audio"
    "gifv" -> "Animated image"
    else -> "Image"
}

@Composable
private fun ImageViewer(url: String, description: String?) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 6f)
        offsetX += panChange.x
        offsetY += panChange.y
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = url,
            contentDescription = description ?: "Image",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                )
                .transformable(state = transform),
        )
        if (!description.isNullOrBlank()) {
            Text(
                description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .semantics { contentDescription = description },
            )
        }
    }
}

@Composable
private fun VideoPlayer(url: String, description: String?) {
    val player = rememberExoPlayer(url)
    Column(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f),
        )
        if (!description.isNullOrBlank()) {
            Text(
                description,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun AudioPlayer(url: String, description: String?) {
    val player = rememberExoPlayer(url)
    val playing by playerIsPlayingAsState(player)
    val position by playerPositionAsState(player)
    val duration by playerDurationAsState(player)
    val progress = if (duration > 0) position.toFloat() / duration else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!description.isNullOrBlank()) {
            Text(description, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = {
                    if (playing) player.pause() else player.play()
                },
                modifier = Modifier.size(96.dp),
            ) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    modifier = Modifier.size(64.dp),
                )
            }
        }
        if (duration > 0) {
            Slider(
                value = progress.coerceIn(0f, 1f),
                onValueChange = { v -> player.seekTo((v * duration).toLong()) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${formatTime(position)} / ${formatTime(duration)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun rememberExoPlayer(url: String): ExoPlayer {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
    return player
}

@Composable
private fun playerIsPlayingAsState(player: ExoPlayer): androidx.compose.runtime.State<Boolean> {
    val state = remember(player) { MutableStateFlow(player.isPlaying) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { state.value = isPlaying }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return state.collectAsState()
}

@Composable
private fun playerPositionAsState(player: ExoPlayer): androidx.compose.runtime.State<Long> {
    val pos = remember(player) { mutableLongStateOf(player.currentPosition) }
    LaunchedEffect(player) {
        while (isActive) {
            pos.longValue = player.currentPosition
            delay(500)
        }
    }
    return pos
}

@Composable
private fun playerDurationAsState(player: ExoPlayer): androidx.compose.runtime.State<Long> {
    val dur = remember(player) { mutableLongStateOf(0L) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) dur.longValue = player.duration.coerceAtLeast(0)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return dur
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}
