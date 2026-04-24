package me.masonasons.fastsm.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.readAvailable
import java.io.File

/**
 * Self-update from a static HTTP endpoint. The version manifest is a plain
 * text file with a single version string like `0.2.0`; if it's greater than
 * the installed version we download the APK and hand off to the system
 * installer via FileProvider.
 *
 * Distribution is sideload-only (masonasons.me), so we don't try to match
 * Play Store semantics — no rollback, no signature pinning beyond whatever
 * the OS already enforces at install time.
 */
class AppUpdater(
    private val context: Context,
    private val httpClient: HttpClient,
) {
    sealed interface Check {
        data object UpToDate : Check
        data class Available(val remoteVersion: String, val installedVersion: String) : Check
        data class Failed(val reason: String) : Check
    }

    suspend fun check(): Check = runCatching {
        val remote = httpClient.get(VERSION_URL).bodyAsText().trim()
        if (remote.isBlank()) return Check.Failed("Empty version response")
        val installed = installedVersion()
        if (isNewer(remote, installed)) Check.Available(remote, installed)
        else Check.UpToDate
    }.getOrElse { Check.Failed(it.message ?: it::class.simpleName ?: "Unknown error") }

    suspend fun downloadAndInstall(): String? {
        val dir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
        // Overwrite any half-downloaded file from a previous aborted run.
        val target = File(dir, "FastSM-update.apk")
        if (target.exists()) target.delete()
        runCatching {
            val response = httpClient.get(APK_URL)
            val channel = response.bodyAsChannel()
            target.outputStream().use { out ->
                val buf = ByteArray(DOWNLOAD_BUFFER)
                while (!channel.isClosedForRead) {
                    val n = channel.readAvailable(buf, 0, buf.size)
                    if (n > 0) out.write(buf, 0, n)
                }
            }
        }.onFailure {
            target.delete()
            return it.message ?: "Download failed"
        }
        if (!target.exists() || target.length() == 0L) return "Downloaded file is empty"

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            target,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching { context.startActivity(intent); null }
            .getOrElse { it.message ?: "Could not launch installer" }
    }

    private fun installedVersion(): String {
        val name = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        // Strip the `-debug` / `-beta` style suffix so a debug build compares
        // against the release manifest cleanly.
        return name.substringBefore('-')
    }

    private fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split('.').mapNotNull { it.toIntOrNull() }
        val l = local.split('.').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }

    private companion object {
        const val VERSION_URL = "https://masonasons.me/projects/FastSMDroidversion.txt"
        const val APK_URL = "https://masonasons.me/softs/FastSM.apk"
        const val DOWNLOAD_BUFFER = 16 * 1024
    }
}
