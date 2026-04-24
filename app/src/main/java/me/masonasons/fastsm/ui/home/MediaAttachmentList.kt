package me.masonasons.fastsm.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import me.masonasons.fastsm.domain.model.UniversalMedia

/**
 * Compact visual list of media attachments. TalkBack discovery is via the
 * parent status's custom actions — the thumbnails themselves are hidden from
 * a11y to avoid announcing them twice.
 */
@Composable
internal fun MediaAttachmentList(
    media: List<UniversalMedia>,
    onOpenMedia: (UniversalMedia) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clearAndSetSemantics { },
    ) {
        media.forEach { m ->
            when (m.type) {
                "image", "gifv" -> ImageThumb(m, onOpenMedia)
                "video" -> VideoThumb(m, onOpenMedia)
                "audio" -> AudioRow(m, onOpenMedia)
                else -> AudioRow(m, onOpenMedia)
            }
        }
    }
}

@Composable
private fun ImageThumb(m: UniversalMedia, onOpen: (UniversalMedia) -> Unit) {
    AsyncImage(
        model = m.previewUrl ?: m.url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onOpen(m) },
    )
}

@Composable
private fun VideoThumb(m: UniversalMedia, onOpen: (UniversalMedia) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onOpen(m) },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = m.previewUrl ?: m.url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth(),
        )
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(48.dp),
        )
    }
}

@Composable
private fun AudioRow(m: UniversalMedia, onOpen: (UniversalMedia) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(m) },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Column {
                Text(mediaLabel(m.type).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge)
                if (!m.description.isNullOrBlank()) {
                    Text(
                        m.description,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
