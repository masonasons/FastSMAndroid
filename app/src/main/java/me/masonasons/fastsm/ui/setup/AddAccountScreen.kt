package me.masonasons.fastsm.ui.setup

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.masonasons.fastsm.domain.model.PlatformType
import me.masonasons.fastsm.util.CustomTabs

@Composable
fun AddAccountScreen(
    viewModel: AddAccountViewModel,
    onLoggedIn: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AddAccountEvent.OpenBrowser -> CustomTabs.launch(context, Uri.parse(event.url))
                is AddAccountEvent.LoggedIn -> onLoggedIn(event.accountId)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Add account", style = MaterialTheme.typography.titleLarge)

        PlatformSelector(
            current = state.platform,
            enabled = !state.busy,
            onSelect = viewModel::onPlatformChanged,
        )

        when (state.platform) {
            PlatformType.MASTODON -> MastodonForm(state, viewModel)
            PlatformType.BLUESKY -> BlueskyForm(state, viewModel)
        }

        Button(onClick = viewModel::startLogin, enabled = !state.busy) {
            Text(
                when (state.status) {
                    Status.Idle -> "Log in"
                    Status.Registering -> "Contacting instance\u2026"
                    Status.AwaitingBrowser -> "Waiting for browser\u2026"
                    Status.ExchangingToken -> "Finishing login\u2026"
                }
            )
        }

        if (state.busy) {
            Spacer(Modifier.height(4.dp))
            CircularProgressIndicator()
        }

        state.error?.let { err ->
            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun PlatformSelector(
    current: PlatformType,
    enabled: Boolean,
    onSelect: (PlatformType) -> Unit,
) {
    val entries = PlatformType.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        entries.forEachIndexed { index, platform ->
            SegmentedButton(
                selected = platform == current,
                onClick = { if (enabled) onSelect(platform) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = entries.size),
                enabled = enabled,
            ) { Text(platform.displayName) }
        }
    }
}

@Composable
private fun MastodonForm(state: AddAccountState, viewModel: AddAccountViewModel) {
    Text(
        "Enter your Mastodon instance. You'll be sent to your browser to log in.",
        style = MaterialTheme.typography.bodyLarge,
    )
    OutlinedTextField(
        value = state.instance,
        onValueChange = viewModel::onInstanceChanged,
        label = { Text("Instance (e.g. mastodon.social)") },
        singleLine = true,
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BlueskyForm(state: AddAccountState, viewModel: AddAccountViewModel) {
    var revealed by remember { mutableStateOf(false) }
    Text(
        "Enter your Bluesky handle and an app password. " +
            "Create one at bsky.app \u2192 Settings \u2192 App Passwords.",
        style = MaterialTheme.typography.bodyLarge,
    )
    OutlinedTextField(
        value = state.handle,
        onValueChange = viewModel::onHandleChanged,
        label = { Text("Handle (e.g. alice.bsky.social)") },
        singleLine = true,
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.appPassword,
        onValueChange = viewModel::onAppPasswordChanged,
        label = { Text("App password") },
        singleLine = true,
        enabled = !state.busy,
        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}
