package me.masonasons.fastsm.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.masonasons.fastsm.domain.model.UniversalStatus
import me.masonasons.fastsm.domain.model.UniversalUser
import me.masonasons.fastsm.platform.mastodon.HtmlStrip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onOpenProfile: (String) -> Unit,
    onOpenThread: (UniversalStatus) -> Unit,
    onAddedHashtag: () -> Unit,
    onClose: () -> Unit,
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SearchEvent.AddedHashtagTimeline -> onAddedHashtag()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChanged,
                label = { Text("Search users, hashtags, posts") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            when {
                loading && results.isEmpty -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                error != null && results.isEmpty -> Text(
                    error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
                query.length < 2 -> Text(
                    "Enter at least 2 characters.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                results.isEmpty -> Text(
                    "No matches.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    if (results.hashtags.isNotEmpty()) {
                        item(key = "hash-header") { SectionHeader("Hashtags") }
                        items(results.hashtags, key = { "hash-$it" }) { tag ->
                            HashtagRow(tag, onClick = { viewModel.addHashtagTimeline(tag) })
                            HorizontalDivider()
                        }
                    }
                    if (results.users.isNotEmpty()) {
                        item(key = "users-header") { SectionHeader("Users") }
                        items(results.users, key = { "u-${it.id}" }) { user ->
                            UserRow(user, onClick = { onOpenProfile(user.id) })
                            HorizontalDivider()
                        }
                    }
                    if (results.posts.isNotEmpty()) {
                        item(key = "posts-header") { SectionHeader("Posts") }
                        items(results.posts, key = { "p-${it.id}" }) { post ->
                            PostRow(post, onClick = { onOpenThread(post) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics { /* read as a heading */ },
        style = MaterialTheme.typography.titleLarge,
    )
}

@Composable
private fun HashtagRow(tag: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "Hashtag #$tag. Activate to add as a timeline."
                onClick { onClick(); true }
            }
            .padding(vertical = 12.dp),
    ) {
        Text("#$tag", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Tap to add as timeline",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UserRow(user: UniversalUser, onClick: () -> Unit) {
    val bio = HtmlStrip.toPlainText(user.note)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = buildString {
                    append(user.displayName)
                    append(" (@").append(user.acct).append(")")
                    if (bio.isNotBlank()) append(". ").append(bio.take(140))
                    append(". Activate to view profile.")
                }
                onClick { onClick(); true }
            }
            .padding(vertical = 12.dp),
    ) {
        Text(user.displayName, style = MaterialTheme.typography.bodyLarge)
        Text(
            "@${user.acct}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (bio.isNotBlank()) {
            Text(
                bio.take(140),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PostRow(post: UniversalStatus, onClick: () -> Unit) {
    val body = post.text.ifBlank { "(no text content)" }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = buildString {
                    append(post.account.displayName)
                    append(" (@").append(post.account.acct).append("): ")
                    append(body.take(200))
                    append(". Activate to open thread.")
                }
                onClick { onClick(); true }
            }
            .padding(vertical = 12.dp),
    ) {
        Text(
            "${post.account.displayName} (@${post.account.acct})",
            style = MaterialTheme.typography.labelLarge,
        )
        Text(body.take(200), style = MaterialTheme.typography.bodyLarge)
    }
}
