package me.masonasons.fastsm.util

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

object CustomTabs {
    fun launch(context: Context, uri: Uri) {
        val intent = CustomTabsIntent.Builder().build()
        intent.launchUrl(context, uri)
    }
}
