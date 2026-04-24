package me.masonasons.fastsm.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import me.masonasons.fastsm.domain.model.UniversalMedia

/**
 * Launches the system's preferred player for a non-image media attachment.
 * Returns true if an activity was found to handle the intent.
 */
object MediaLauncher {
    fun launch(context: Context, media: UniversalMedia): Boolean {
        val mime = when (media.type) {
            "video" -> "video/*"
            "audio" -> "audio/*"
            else -> null
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            val uri = Uri.parse(media.url)
            if (mime != null) setDataAndType(uri, mime) else data = uri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
