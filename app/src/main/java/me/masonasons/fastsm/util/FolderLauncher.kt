package me.masonasons.fastsm.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.io.File

/**
 * Try to open a file manager at [dir]. Most Android file managers ignore a
 * caller-supplied folder URI — scoped storage makes it unreliable — so we
 * optimistically fire an ACTION_VIEW intent; if nothing resolves, we fall
 * back to copying the absolute path to the clipboard and toasting the user
 * so they can paste it into their file manager.
 */
object FolderLauncher {

    fun open(context: Context, dir: File) {
        if (!dir.exists()) dir.mkdirs()
        val intent = Intent(Intent.ACTION_VIEW).apply {
            val uri = Uri.parse("file://${dir.absolutePath}")
            setDataAndType(uri, "resource/folder")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val resolved = intent.resolveActivity(context.packageManager)
        if (resolved != null) {
            runCatching { context.startActivity(Intent.createChooser(intent, "Open folder")) }
                .onSuccess { return }
        }
        // Fallback: copy path + toast.
        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clip?.setPrimaryClip(ClipData.newPlainText("Soundpack folder", dir.absolutePath))
        Toast.makeText(
            context,
            "Path copied to clipboard. Open your file manager and paste.",
            Toast.LENGTH_LONG,
        ).show()
    }
}
