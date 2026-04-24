package me.masonasons.fastsm.data.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import me.masonasons.fastsm.FastSmApplication
import me.masonasons.fastsm.MainActivity
import me.masonasons.fastsm.domain.model.NotificationType
import me.masonasons.fastsm.domain.model.UniversalNotification

/**
 * Background poller. On each run:
 *  1. Fetch the notifications endpoint for every signed-in account.
 *  2. Filter to items whose id is newer than our stored high-water mark.
 *  3. Surface each new item as an Android system notification.
 *  4. Update the high-water mark.
 *
 * The worker is scheduled periodically via [NotificationScheduler]. Running
 * inside WorkManager means the OS handles battery / network backoff — no
 * explicit retry logic here; a failed fetch just retries at the next tick.
 */
class NotificationPollingWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext.applicationContext as? FastSmApplication
            ?: return Result.success()
        val container = app.container
        val prefs = container.notificationPrefs
        if (!prefs.enabled.value) return Result.success()
        // Bail early if the user revoked POST_NOTIFICATIONS — we'd silently
        // burn cycles fetching otherwise. On API <= 32 this always passes.
        if (!hasPostPermission()) return Result.success()

        ensureChannel()
        val accounts = container.accountRepository.getAll()
        for (account in accounts) {
            val platform = container.platformFactory.forAccount(account)
            val fetched = runCatching { platform.getNotifications(limit = 20) }.getOrNull()
                ?: continue
            val lastSeen = prefs.lastSeenId(account.id)
            // Assume ids come newest-first; take-while-newer then reverse so
            // we post the oldest of the new batch first and the newest most
            // recently (matches a natural read order for users).
            val fresh = if (lastSeen == null) {
                emptyList() // first run: establish baseline, don't spam with history
            } else {
                fetched.takeWhile { it.id != lastSeen }
            }
            fetched.firstOrNull()?.id?.let { prefs.setLastSeenId(account.id, it) }
            if (fresh.size > SUMMARY_THRESHOLD) {
                postSummary(account.label, fresh)
            } else {
                fresh.asReversed().forEach { notif -> postNotification(account.label, notif) }
            }
        }
        return Result.success()
    }

    private fun postSummary(accountLabel: String, fresh: List<UniversalNotification>) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val lines = fresh.asReversed().take(8).map { buildTitle(accountLabel, it) }
        val inbox = NotificationCompat.InboxStyle().also { s ->
            lines.forEach(s::addLine)
            if (fresh.size > lines.size) s.setSummaryText("+${fresh.size - lines.size} more")
        }
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("${fresh.size} new notifications · $accountLabel")
            .setContentText(lines.firstOrNull().orEmpty())
            .setStyle(inbox)
            .setAutoCancel(true)
            .setContentIntent(pending)
        // Per-account summary id so two accounts don't stomp on each other.
        NotificationManagerCompat.from(applicationContext)
            .notify("summary:$accountLabel".hashCode(), builder.build())
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Social notifications",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Mentions, replies, boosts, favourites, and follows from your timelines."
        }
        mgr.createNotificationChannel(channel)
    }

    private fun postNotification(accountLabel: String, notif: UniversalNotification) {
        val title = buildTitle(accountLabel, notif)
        val body = notif.status?.text?.take(200).orEmpty()
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            // Use a stock system icon so we don't need to ship a custom asset
            // just for this; it's a neutral speech-bubble-ish glyph.
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
        if (body.isNotBlank()) builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        // Stable id from the notification's hash so the same push doesn't
        // stack up on repeated runs.
        NotificationManagerCompat.from(applicationContext)
            .notify(notif.id.hashCode(), builder.build())
    }

    private fun buildTitle(accountLabel: String, n: UniversalNotification): String {
        val who = n.account.displayName
        val action = when (n.type) {
            NotificationType.MENTION -> "mentioned you"
            NotificationType.REPLY -> "replied to your post"
            NotificationType.QUOTE -> "quoted your post"
            NotificationType.REBLOG -> "boosted your post"
            NotificationType.FAVOURITE -> "favourited your post"
            NotificationType.FOLLOW -> "followed you"
            NotificationType.FOLLOW_REQUEST -> "requested to follow you"
            NotificationType.POLL -> "poll ended"
            NotificationType.UPDATE -> "edited a post"
            NotificationType.STATUS -> "posted"
            NotificationType.OTHER -> n.typeRaw
        }
        return "$who $action · $accountLabel"
    }

    private fun hasPostPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val CHANNEL_ID = "fastsm_social"
        const val WORK_NAME = "fastsm_notification_poll"
        // Above this many new notifications in a single poll we collapse into
        // one summary entry so the user isn't drowned in per-post posts.
        private const val SUMMARY_THRESHOLD = 3
    }
}
