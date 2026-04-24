package me.masonasons.fastsm

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.masonasons.fastsm.platform.mastodon.MastodonOAuth
import me.masonasons.fastsm.ui.nav.FastSmNavGraph
import me.masonasons.fastsm.ui.setup.OAuthCallback
import me.masonasons.fastsm.ui.theme.FastSmTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as FastSmApplication).container
        handleOAuthIntent(intent)

        setContent {
            FastSmTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    FastSmNavGraph(container = container)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Dismiss any pending system notifications so the launcher badge count
        // clears as soon as the user is actually looking at the app.
        runCatching { NotificationManagerCompat.from(this).cancelAll() }
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        if (data.scheme != "fastsm" || data.host != "oauth") return
        val container = (application as FastSmApplication).container
        val callback = MastodonOAuth.extractError(data)?.let(OAuthCallback::Error)
            ?: MastodonOAuth.extractCode(data)?.let(OAuthCallback::Code)
            ?: return
        lifecycleScope.launch { container.oauthCoordinator.emit(callback) }
    }
}
