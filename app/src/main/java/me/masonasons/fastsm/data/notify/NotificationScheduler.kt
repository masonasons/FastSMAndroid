package me.masonasons.fastsm.data.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules / cancels the notification poller. Android enforces a 15-minute
 * minimum for periodic work — we don't try to beat it. The work runs only
 * when the network's available; if nothing's online this run we just wait
 * until next tick rather than thrash battery retrying.
 */
object NotificationScheduler {

    fun setEnabled(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(NotificationPollingWorker.WORK_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<NotificationPollingWorker>(
            15, TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .build()
        // KEEP policy: if the work is already scheduled with equivalent
        // params, leave it — no interval drift from repeated Settings toggles.
        wm.enqueueUniquePeriodicWork(
            NotificationPollingWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
