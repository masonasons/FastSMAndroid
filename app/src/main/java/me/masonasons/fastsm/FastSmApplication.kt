package me.masonasons.fastsm

import android.app.Application
import me.masonasons.fastsm.data.notify.NotificationScheduler
import me.masonasons.fastsm.di.AppContainer

class FastSmApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Re-assert the WorkManager schedule on cold start: the OS drops
        // periodic work after certain events (force-stop, package updates),
        // and KEEP policy means this is a no-op when the work already exists.
        if (container.notificationPrefs.enabled.value) {
            NotificationScheduler.setEnabled(this, true)
        }
    }
}
