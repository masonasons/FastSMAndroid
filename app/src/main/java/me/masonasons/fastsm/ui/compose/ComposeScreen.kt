package me.masonasons.fastsm.ui.compose

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.masonasons.fastsm.domain.model.Visibility
import androidx.compose.ui.text.input.ImeAction
import me.masonasons.fastsm.ui.home.LocalAutoFocusCompose
import me.masonasons.fastsm.ui.home.LocalSubmitOnImeAction
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    viewModel: ComposeViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val autoFocus = LocalAutoFocusCompose.current
    val submitOnIme = LocalSubmitOnImeAction.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val textFocusRequester = remember { FocusRequester() }

    // Local TextFieldValue so we can place the cursor at the end of the
    // prefilled text (@mentions) instead of the default position 0. Seeded
    // exactly once — after the ViewModel's async init populates the state —
    // so subsequent user edits aren't clobbered.
    var textField by remember { mutableStateOf(TextFieldValue("")) }
    var seeded by remember { mutableStateOf(false) }
    LaunchedEffect(state.accountLabel, state.text) {
        if (!seeded && state.accountLabel.isNotEmpty()) {
            textField = TextFieldValue(state.text, selection = TextRange(state.text.length))
            seeded = true
            if (autoFocus) {
                // Seed completed — request focus so the keyboard raises. Some
                // OEMs refuse requestFocus before the field is attached, so we
                // also prod the keyboard controller explicitly.
                textFocusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // Read the content URI off the main thread — can be large (photos).
            val payload = withContext(Dispatchers.IO) {
                val resolver = context.contentResolver
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    ?: "attachment"
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                Triple(bytes, name, mime)
            }
            val bytes = payload.first
            if (bytes != null) viewModel.addMedia(bytes, payload.second, payload.third)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ComposeEvent.Posted -> onClose()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEdit) "Edit post" else "New post") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::submit,
                        enabled = state.canSubmit,
                    ) {
                        Text(
                            when {
                                state.busy && state.isEdit -> "Saving\u2026"
                                state.isEdit -> "Save"
                                state.busy && state.scheduledAt != null -> "Scheduling\u2026"
                                state.scheduledAt != null -> "Schedule"
                                state.busy -> "Posting\u2026"
                                else -> "Post"
                            }
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "${state.accountLabel} \u00b7 ${state.platformName}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.replyToAuthor?.let { author ->
                ParentPreview(
                    author = author,
                    body = state.replyToText,
                    spoiler = state.replyToSpoiler,
                    label = "Replying to",
                )
            }
            if (state.isQuote) {
                ParentPreview(
                    author = state.quoteAuthor.orEmpty(),
                    body = state.quoteText,
                    spoiler = null,
                    label = "Quoting",
                )
            }

            if (state.supportsContentWarning) {
                OutlinedTextField(
                    value = state.spoilerText,
                    onValueChange = viewModel::onSpoilerChanged,
                    label = { Text("Content warning (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = textField,
                onValueChange = {
                    textField = it
                    viewModel.onTextChanged(it.text)
                },
                label = { Text("What's on your mind?") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(textFocusRequester),
                minLines = 4,
                // ImeAction.Send exposes a send/submit button to the IME and
                // is what TalkBack's Braille Keyboard "Submit" gesture fires.
                // With a multi-line field the regular newline button is still
                // available — only the explicit Send action calls submit().
                keyboardOptions = if (submitOnIme) {
                    KeyboardOptions(imeAction = ImeAction.Send)
                } else KeyboardOptions.Default,
                keyboardActions = KeyboardActions(
                    onSend = if (submitOnIme) {
                        { viewModel.submit() }
                    } else null,
                ),
            )

            // Mention picker — insert @acct at the current cursor position.
            var mentionDialogOpen by remember { mutableStateOf(false) }
            OutlinedButton(onClick = {
                viewModel.clearMentionSearch()
                mentionDialogOpen = true
            }) {
                Icon(Icons.Filled.AlternateEmail, contentDescription = null)
                Text("  Mention user")
            }
            if (mentionDialogOpen) {
                MentionPickerDialog(
                    query = state.mentionQuery,
                    results = state.mentionResults,
                    searching = state.mentionSearching,
                    onQueryChange = viewModel::searchMentions,
                    onSelect = { user ->
                        val insert = "@${user.acct} "
                        val before = textField.text.substring(0, textField.selection.start)
                        val after = textField.text.substring(textField.selection.end)
                        val padLeft = if (before.isEmpty() || before.endsWith(' ')) "" else " "
                        val newText = before + padLeft + insert + after
                        val caret = (before + padLeft + insert).length
                        textField = TextFieldValue(newText, selection = TextRange(caret))
                        viewModel.onTextChanged(newText)
                        mentionDialogOpen = false
                    },
                    onDismiss = { mentionDialogOpen = false },
                )
            }

            // Mastodon forbids combining media + poll on the same post.
            val pollActive = state.poll != null
            val mediaActive = state.attachments.isNotEmpty()

            if (state.supportsMedia && !state.isEdit && !pollActive) {
                AttachmentSection(
                    attachments = state.attachments,
                    canAddMore = state.attachments.size < state.maxMediaAttachments,
                    onAdd = { mediaPicker.launch("image/*") },
                    onRemove = viewModel::removeAttachment,
                    onDescriptionChange = viewModel::updateAttachmentDescription,
                )
                RecorderSection(
                    enabled = state.attachments.size < state.maxMediaAttachments,
                    onFinish = { bytes, filename, mime ->
                        viewModel.addMedia(bytes, filename, mime)
                    },
                )
            }

            if (state.supportsMedia && !state.isEdit && !mediaActive) {
                var pollDialogOpen by remember { mutableStateOf(false) }
                PollSection(
                    poll = state.poll,
                    onOpen = { pollDialogOpen = true },
                    onRemove = { viewModel.setPoll(null) },
                )
                if (pollDialogOpen) {
                    PollDialog(
                        initial = state.poll,
                        onDismiss = { pollDialogOpen = false },
                        onConfirm = { viewModel.setPoll(it); pollDialogOpen = false },
                    )
                }
            }

            if (state.supportsScheduling && !state.isEdit) {
                ScheduleSection(
                    scheduledAt = state.scheduledAt,
                    onPick = viewModel::setScheduledAt,
                    onClear = { viewModel.setScheduledAt(null) },
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.supportsVisibility) {
                    VisibilityDropdown(
                        current = state.visibility,
                        onSelect = viewModel::onVisibilityChanged,
                    )
                }
                Text(
                    "${state.remaining}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (state.remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.error?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * Read-only preview of the post being replied to. Rendered as a single
 * semantic block — "Replying to @alice: [CW: …] body" — so TalkBack reads it
 * once as context, instead of announcing each line separately.
 */
@Composable
private fun ParentPreview(
    author: String,
    body: String?,
    spoiler: String?,
    label: String = "Replying to",
) {
    val spoken = buildString {
        append(label).append(" @").append(author)
        if (!spoiler.isNullOrBlank()) append(". Content warning: ").append(spoiler)
        if (!body.isNullOrBlank()) append(". ").append(body)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(12.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "$label @$author",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (!spoiler.isNullOrBlank()) {
            Text("CW: $spoiler", style = MaterialTheme.typography.bodyLarge)
        }
        if (!body.isNullOrBlank()) {
            Text(body, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * Schedule summary + "Schedule" button. When a time is set, show the picked
 * datetime and a "Clear" button to revert to immediate posting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleSection(
    scheduledAt: Instant?,
    onPick: (Instant) -> Unit,
    onClear: () -> Unit,
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var pickedDateMs by remember { mutableStateOf<Long?>(null) }

    if (scheduledAt == null) {
        OutlinedButton(onClick = { showDate = true }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("  Schedule post")
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Scheduled for ${formatScheduled(scheduledAt)}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showDate = true }) { Text("Change") }
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, contentDescription = "Clear schedule")
            }
        }
    }

    if (showDate) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = scheduledAt?.toEpochMilli()
                ?: (System.currentTimeMillis() + 60 * 60 * 1000), // default 1hr out
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickedDateMs = datePickerState.selectedDateMillis
                    showDate = false
                    if (pickedDateMs != null) showTime = true
                }) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTime) {
        val now = LocalDateTime.now().plusHours(1)
        val initial = scheduledAt?.atZone(ZoneId.systemDefault())?.toLocalDateTime() ?: now
        val timePickerState = rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
        )
        androidx.compose.ui.window.Dialog(onDismissRequest = { showTime = false }) {
            Column(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(16.dp),
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Pick a time", style = MaterialTheme.typography.titleLarge)
                TimePicker(state = timePickerState)
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { showTime = false }) { Text("Cancel") }
                    TextButton(onClick = {
                        val dateMs = pickedDateMs ?: return@TextButton
                        val zone = ZoneId.systemDefault()
                        val datePart = Instant.ofEpochMilli(dateMs).atZone(ZoneId.of("UTC")).toLocalDate()
                        val localDateTime = datePart.atTime(timePickerState.hour, timePickerState.minute)
                        onPick(localDateTime.atZone(zone).toInstant())
                        showTime = false
                    }) { Text("Schedule") }
                }
            }
        }
    }
}

private val scheduleFormatter = DateTimeFormatter.ofPattern("MMM d yyyy, h:mm a")

private fun formatScheduled(instant: Instant): String =
    instant.atZone(ZoneId.systemDefault()).format(scheduleFormatter)

/**
 * Poll summary + "Add poll"/"Edit poll" button. When a poll is active, we
 * show a one-line summary so TalkBack users can tell it's attached.
 */
@Composable
private fun PollSection(
    poll: me.masonasons.fastsm.domain.model.PollSpec?,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    if (poll == null) {
        OutlinedButton(onClick = onOpen) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("  Add poll")
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Poll: ${poll.options.size} options, ${formatDuration(poll.expiresInSec)}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpen) { Text("Edit") }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove poll")
            }
        }
    }
}

private fun formatDuration(seconds: Int): String = when {
    seconds < 3_600 -> "${seconds / 60} min"
    seconds < 86_400 -> "${seconds / 3_600} hr"
    else -> "${seconds / 86_400} day${if (seconds / 86_400 == 1) "" else "s"}"
}

/**
 * Media attachment list + "Add media" button. Each attachment exposes its
 * alt-text field; TalkBack users land on the field with a clear label like
 * "Alt text for photo.jpg".
 */
@Composable
private fun AttachmentSection(
    attachments: List<AttachedMedia>,
    canAddMore: Boolean,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onDescriptionChange: (String, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        attachments.forEach { att ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (att.uploading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(4.dp)
                                .clearAndSetSemantics { contentDescription = "Uploading ${att.filename}" },
                        )
                    }
                    Text(
                        att.filename,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onRemove(att.localId) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove ${att.filename}")
                    }
                }
                att.error?.let { err ->
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                }
                OutlinedTextField(
                    value = att.description,
                    onValueChange = { onDescriptionChange(att.localId, it) },
                    label = { Text("Alt text for ${att.filename}") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !att.uploading && att.error == null,
                )
            }
        }
        OutlinedButton(
            onClick = onAdd,
            enabled = canAddMore,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("  Add media")
        }
    }
}

@Composable
private fun VisibilityDropdown(
    current: Visibility,
    onSelect: (Visibility) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Button(onClick = { expanded = true }) {
        Text(current.label)
        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        Visibility.entries.forEach { v ->
            DropdownMenuItem(
                text = { Text(v.label) },
                onClick = { onSelect(v); expanded = false },
            )
        }
    }
}
