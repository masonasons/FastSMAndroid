package me.masonasons.fastsm.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.masonasons.fastsm.data.notify.NotificationScheduler
import me.masonasons.fastsm.domain.model.PostAction
import me.masonasons.fastsm.domain.template.CwMode
import me.masonasons.fastsm.domain.template.PostTemplateRenderer
import me.masonasons.fastsm.util.FolderLauncher

/**
 * Categories the settings screen is split into. Each has a sub-screen with
 * its own `Scaffold` + back button. Categories kept separate so TalkBack
 * users don't have to swipe through unrelated toggles to reach what they
 * want — and so LazyColumn-based scrolling always follows focus.
 */
private enum class SettingsCategory(val title: String, val description: String) {
    GENERAL("General", "Streaming, timeline loading, home position sync"),
    ACTIONS("Post actions", "Which TalkBack custom actions appear on a post"),
    TEMPLATES("Post formatting", "Customize how posts and notifications are spoken"),
    SPEECH("Speech", "What the app announces through TalkBack"),
    SOUND("Sound", "Soundpack and per-event sound effects"),
    HAPTICS("Haptics", "Vibration feedback"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onClose: () -> Unit,
) {
    // A single `currentCategory` state drives which sub-screen is visible.
    // Intercepting back here (via onClose behavior in subscreens) is simpler
    // than threading extra nav routes for every category.
    var currentCategory by remember { mutableStateOf<SettingsCategory?>(null) }

    // System back while a sub-screen is open should return to the category
    // list, not pop the whole Settings route off the nav stack.
    BackHandler(enabled = currentCategory != null) {
        currentCategory = null
    }

    when (val cat = currentCategory) {
        null -> CategoryList(
            onSelect = { currentCategory = it },
            onClose = onClose,
        )
        else -> CategoryDetail(
            category = cat,
            viewModel = viewModel,
            onBack = { currentCategory = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryList(
    onSelect: (SettingsCategory) -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close settings")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            items(SettingsCategory.values().toList()) { cat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = false,
                            role = Role.Button,
                            onValueChange = { onSelect(cat) },
                        )
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(cat.title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            cat.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDetail(
    category: SettingsCategory,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to settings")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (category) {
                SettingsCategory.GENERAL -> generalItems(viewModel)
                SettingsCategory.ACTIONS -> actionsItems(viewModel)
                SettingsCategory.TEMPLATES -> templateItems(viewModel)
                SettingsCategory.SPEECH -> speechItems(viewModel)
                SettingsCategory.SOUND -> soundItems(viewModel)
                SettingsCategory.HAPTICS -> hapticsItems(viewModel)
            }
        }
    }
}

// --- Per-category item lists ---

private fun androidx.compose.foundation.lazy.LazyListScope.generalItems(viewModel: SettingsViewModel) {
    item("streaming") {
        val v by viewModel.streamingEnabled.collectAsStateWithLifecycle()
        SettingRow(
            title = "Realtime streaming",
            description = "When on, new posts and notifications appear automatically. Mastodon only.",
            checked = v,
            onCheckedChange = viewModel::setStreamingEnabled,
        )
        HorizontalDivider()
    }
    item("marker") {
        val v by viewModel.markerSyncEnabled.collectAsStateWithLifecycle()
        SettingRow(
            title = "Sync home position",
            description = "Save and restore the home timeline read position via your Mastodon server, so reads sync across devices.",
            checked = v,
            onCheckedChange = viewModel::setMarkerSyncEnabled,
        )
        HorizontalDivider()
    }
    item("remember_positions") {
        val v by viewModel.rememberTimelinePositions.collectAsStateWithLifecycle()
        SettingRow(
            title = "Remember timeline positions",
            description = "Restore your spot in every opened timeline (local, federated, lists, hashtags, …) across app restarts. Also acts as a fallback for home when server sync is off or unavailable.",
            checked = v,
            onCheckedChange = viewModel::setRememberTimelinePositions,
        )
        HorizontalDivider()
    }
    item("pages") {
        val pages by viewModel.fetchPages.collectAsStateWithLifecycle()
        StepperRow(
            title = "Timeline pages per load",
            description = "Number of API calls to make when loading a timeline. More pages means more posts up front, slower refresh.",
            value = pages,
            min = viewModel.fetchPagesMin,
            max = viewModel.fetchPagesMax,
            onChange = viewModel::setFetchPages,
        )
    }
    item("autofocus") {
        val v by viewModel.autoFocusCompose.collectAsStateWithLifecycle()
        SettingRow(
            title = "Auto-focus compose keyboard",
            description = "When you open New post, Reply, or Quote, focus the text field and show the keyboard immediately.",
            checked = v,
            onCheckedChange = viewModel::setAutoFocusCompose,
        )
    }
    item("ime_submit") {
        val v by viewModel.submitOnImeAction.collectAsStateWithLifecycle()
        SettingRow(
            title = "Submit post on keyboard submit gesture",
            description = "Makes Braille Keyboard's submit gesture (and the Send action on other IMEs) post immediately. Newlines still insert normally.",
            checked = v,
            onCheckedChange = viewModel::setSubmitOnImeAction,
        )
    }
    item("bg_notifications") {
        BackgroundNotificationsRow(viewModel)
    }
    item("battery_opt") {
        BatteryOptimizationRow()
    }
    item("updater") {
        UpdaterRow(viewModel)
    }
}

@Composable
private fun UpdaterRow(viewModel: SettingsViewModel) {
    val state by viewModel.updateState.collectAsStateWithLifecycle()
    val (status, actionLabel, action) = when (val s = state) {
        SettingsViewModel.UpdateUiState.Idle ->
            Triple("Check masonasons.me for a newer build.", "Check", viewModel::checkForUpdate)
        SettingsViewModel.UpdateUiState.Checking ->
            Triple("Checking for updates…", "Check", null)
        SettingsViewModel.UpdateUiState.UpToDate ->
            Triple("You're on the latest version.", "Check again", viewModel::checkForUpdate)
        is SettingsViewModel.UpdateUiState.Available ->
            Triple(
                "New version ${s.remoteVersion} available (you're on ${s.installedVersion}).",
                "Download & install",
                viewModel::downloadAndInstall,
            )
        SettingsViewModel.UpdateUiState.Downloading ->
            Triple("Downloading update…", "Downloading…", null)
        is SettingsViewModel.UpdateUiState.Error ->
            Triple("Update failed: ${s.message}", "Retry", viewModel::checkForUpdate)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("App update", style = MaterialTheme.typography.titleLarge)
            Text(
                status,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(enabled = action != null, onClick = { action?.invoke() }) { Text(actionLabel) }
    }
}

@Composable
private fun BackgroundNotificationsRow(viewModel: SettingsViewModel) {
    val enabled by viewModel.backgroundNotifications.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Permission launcher used when enabling on Android 13+: Settings writes
    // the pref only after the user's answered the prompt, so a denial leaves
    // the toggle off rather than claiming we're enabled without permission.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setBackgroundNotifications(true)
            NotificationScheduler.setEnabled(context, true)
        }
    }
    SettingRow(
        title = "Background notifications",
        description = "Poll every 15 minutes for new notifications (mentions, replies, boosts, favourites, follows) and surface them to Android.",
        checked = enabled,
        onCheckedChange = { turnOn ->
            if (!turnOn) {
                viewModel.setBackgroundNotifications(false)
                NotificationScheduler.setEnabled(context, false)
                return@SettingRow
            }
            val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            val granted = !needsPermission || ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.setBackgroundNotifications(true)
                NotificationScheduler.setEnabled(context, true)
            } else {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
    )
}

@Composable
private fun BatteryOptimizationRow() {
    val context = LocalContext.current
    val powerManager = remember {
        context.getSystemService(android.content.Context.POWER_SERVICE) as? PowerManager
    }
    // Re-read on every resume: the user leaves the app to grant the exemption
    // in the system dialog, then comes back — the toggle state changed out
    // from under us while we weren't resumed.
    var exempt by remember {
        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        exempt = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Ignore battery optimization", style = MaterialTheme.typography.titleLarge)
            Text(
                if (exempt)
                    "Granted. Background notification polling will run on schedule."
                else
                    "Without this, Android throttles background work and notifications may arrive late or not at all.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            enabled = !exempt,
            onClick = {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(intent) }.onFailure {
                    // Fall back to the generic battery-optimization list; the user
                    // can still find the app there.
                    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(fallback) }
                }
            },
        ) { Text(if (exempt) "Granted" else "Grant") }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.actionsItems(viewModel: SettingsViewModel) {
    item("header") {
        Text(
            "Toggle which TalkBack custom actions appear on posts and notifications.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
    items(
        items = PostAction.values().toList(),
        key = { it.key },
    ) { action ->
        val enabled by viewModel.enabledPostActions.collectAsStateWithLifecycle()
        SettingRow(
            title = action.label,
            description = null,
            checked = action in enabled,
            onCheckedChange = { viewModel.setPostActionEnabled(action, it) },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.templateItems(viewModel: SettingsViewModel) {
    item("intro") {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Placeholders in $-signs are replaced with post fields.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Available: \$text\$, \$spoiler_text\$, \$visibility\$, \$created_at\$, " +
                    "\$favourites_count\$, \$boosts_count\$, \$replies_count\$, " +
                    "\$url\$, \$account.display_name\$, \$account.acct\$, \$account.username\$, " +
                    "\$account.url\$, \$reblog.<field>\$, and in notifications \$type\$, \$status.<field>\$.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()
    }
    item("cw_mode") {
        CwModeRow(viewModel)
        HorizontalDivider()
    }
    item("include_media") {
        val v by viewModel.includeMediaDescriptions.collectAsStateWithLifecycle()
        SettingRow(
            title = "Include media descriptions",
            description = "Append attachment type and alt text to \$text\$ when rendering posts. Notifications are never auto-appended, matching desktop.",
            checked = v,
            onCheckedChange = viewModel::setIncludeMediaDescriptions,
        )
        HorizontalDivider()
    }
    item("demojify") {
        val v by viewModel.demojifyDisplayNames.collectAsStateWithLifecycle()
        SettingRow(
            title = "Remove emojis from display names",
            description = "Strip unicode emoji and custom :shortcodes: out of display names before TalkBack reads them. Falls back to the handle if the whole name was emoji.",
            checked = v,
            onCheckedChange = viewModel::setDemojifyDisplayNames,
        )
        HorizontalDivider()
    }
    item("max_usernames") {
        val v by viewModel.maxUsernamesDisplay.collectAsStateWithLifecycle()
        StepperRow(
            title = "Collapse leading usernames past",
            description = "When a post starts with more than this many @mentions, collapse them to \"@first and N more …\". 0 disables.",
            value = v,
            min = 0,
            max = viewModel.maxUsernamesDisplayMax,
            onChange = viewModel::setMaxUsernamesDisplay,
        )
        HorizontalDivider()
    }
    item("post") {
        val v by viewModel.postTemplate.collectAsStateWithLifecycle()
        TemplateEditor(
            title = "Post template",
            value = v,
            default = PostTemplateRenderer.DEFAULT_POST,
            onChange = viewModel::setPostTemplate,
        )
    }
    item("boost") {
        val v by viewModel.boostTemplate.collectAsStateWithLifecycle()
        TemplateEditor(
            title = "Boost template",
            value = v,
            default = PostTemplateRenderer.DEFAULT_BOOST,
            onChange = viewModel::setBoostTemplate,
        )
    }
    item("notif") {
        val v by viewModel.notificationTemplate.collectAsStateWithLifecycle()
        TemplateEditor(
            title = "Notification template",
            value = v,
            default = PostTemplateRenderer.DEFAULT_NOTIFICATION,
            onChange = viewModel::setNotificationTemplate,
        )
    }
}

@Composable
private fun CwModeRow(viewModel: SettingsViewModel) {
    val mode by viewModel.cwMode.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Content warning handling", style = MaterialTheme.typography.titleLarge)
            Text(
                "How CW/spoiler text is folded into \$text\$ when a post has one.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = { expanded = true }) { Text(mode.label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CwMode.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        viewModel.setCwMode(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun TemplateEditor(
    title: String,
    value: String,
    default: String,
    onChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        Button(
            onClick = { onChange(default) },
            enabled = value != default,
        ) { Text("Reset to default") }
    }
    HorizontalDivider()
}

private fun androidx.compose.foundation.lazy.LazyListScope.speechItems(viewModel: SettingsViewModel) {
    item("master") {
        val v by viewModel.speechEnabled.collectAsStateWithLifecycle()
        SettingRow(
            title = "Enable speech feedback",
            description = "Announcements are routed through TalkBack — your screen reader speaks them.",
            checked = v,
            onCheckedChange = viewModel::setSpeechEnabled,
        )
        HorizontalDivider()
    }
    item("tabloaded") {
        val v by viewModel.speakTabLoaded.collectAsStateWithLifecycle()
        SettingRow("Posts loaded count", null, v, viewModel::setSpeakTabLoaded)
    }
    item("notif") {
        val v by viewModel.speakNotification.collectAsStateWithLifecycle()
        SettingRow("New notifications", null, v, viewModel::setSpeakNotification)
    }
    item("post") {
        val v by viewModel.speakPostSent.collectAsStateWithLifecycle()
        SettingRow("Post sent confirmation", null, v, viewModel::setSpeakPostSent)
    }
    item("err") {
        val v by viewModel.speakError.collectAsStateWithLifecycle()
        SettingRow("Errors", null, v, viewModel::setSpeakError)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.soundItems(viewModel: SettingsViewModel) {
    item("master") {
        val v by viewModel.soundEnabled.collectAsStateWithLifecycle()
        SettingRow(
            title = "Enable sound effects",
            description = "Place .ogg soundpack folders under the app's external files directory — one folder per pack.",
            checked = v,
            onCheckedChange = viewModel::setSoundEnabled,
        )
        HorizontalDivider()
    }
    item("pack-hint") {
        Text(
            "Soundpack is per-account. Open the account picker in the home screen → tap the active account → Account settings to change it.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
    item("volume") {
        val vol by viewModel.soundVolume.collectAsStateWithLifecycle()
        VolumeSlider(value = vol, onChange = viewModel::setSoundVolume)
    }
    item("open_folder") {
        OpenSoundsFolderRow()
    }
}

@Composable
private fun OpenSoundsFolderRow() {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Soundpacks folder", style = MaterialTheme.typography.titleLarge)
            Text(
                "Opens the external files directory where custom soundpack folders live. If your file manager can't jump there, the path is copied to your clipboard as a fallback.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = {
            val dir = context.getExternalFilesDir("sounds") ?: return@Button
            FolderLauncher.open(context, dir)
        }) { Text("Open") }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.hapticsItems(viewModel: SettingsViewModel) {
    item("master") {
        val v by viewModel.hapticsEnabled.collectAsStateWithLifecycle()
        SettingRow(
            title = "Enable haptics",
            description = "Master toggle for vibration feedback.",
            checked = v,
            onCheckedChange = viewModel::setHapticsEnabled,
        )
        HorizontalDivider()
    }
    item("post") {
        val v by viewModel.hapticsPostSent.collectAsStateWithLifecycle()
        SettingRow("On post sent", null, v, viewModel::setHapticsPostSent)
    }
    item("newpost") {
        val v by viewModel.hapticsNewPost.collectAsStateWithLifecycle()
        SettingRow("On new post arrived", null, v, viewModel::setHapticsNewPost)
    }
    item("notif") {
        val v by viewModel.hapticsNotification.collectAsStateWithLifecycle()
        SettingRow("On new notification", null, v, viewModel::setHapticsNotification)
    }
    item("err") {
        val v by viewModel.hapticsError.collectAsStateWithLifecycle()
        SettingRow("On error", null, v, viewModel::setHapticsError)
    }
}

// --- Reusable rows ---

@Composable
private fun SettingRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    // Modifier.toggleable bundles role=Switch + ToggleableState + click action,
    // so TalkBack announces "Switch, on/off, double tap to toggle" for the
    // whole row. The inner Switch is stateless (onCheckedChange = null) — the
    // Row owns the interaction.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (!description.isNullOrBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun StepperRow(
    title: String,
    description: String?,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics { stateDescription = "$value" },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (!description.isNullOrBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(
            onClick = { if (value > min) onChange(value - 1) },
            enabled = value > min,
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease $title")
        }
        Text(
            "$value",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { stateDescription = "$value of $max" },
        )
        IconButton(
            onClick = { if (value < max) onChange(value + 1) },
            enabled = value < max,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Increase $title")
        }
    }
}

@Composable
private fun SoundpackPicker(
    current: String,
    available: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Soundpack", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        Button(onClick = { expanded = true }) { Text(current) }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            available.forEach { pack ->
                DropdownMenuItem(
                    text = { Text(pack) },
                    onClick = { onSelect(pack); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun VolumeSlider(
    value: Float,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "Sound volume: ${(value * 100).toInt()}%",
            style = MaterialTheme.typography.titleLarge,
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = 0f..1f,
        )
    }
}
