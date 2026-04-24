package me.masonasons.fastsm.ui.thread

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.masonasons.fastsm.domain.model.UniversalMedia
import me.masonasons.fastsm.domain.model.UniversalStatus
import me.masonasons.fastsm.ui.home.StatusItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    viewModel: ThreadViewModel,
    onReply: (UniversalStatus) -> Unit,
    onOpenThread: (UniversalStatus) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenMedia: (UniversalMedia) -> Unit,
    onOpenLink: (String) -> Unit,
    onCompose: () -> Unit,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.closed) {
        if (state.closed) onClose()
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ThreadEvent.OpenCompose -> onCompose()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Thread") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close thread")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh thread")
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.loading && state.target == null -> {
                Box(
                    Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
            state.error != null && state.target == null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Could not load thread", style = MaterialTheme.typography.titleLarge)
                    Text(state.error.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Try again")
                    }
                }
            }
            state.target == null -> {
                Box(
                    Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) { Text("Thread not available") }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                ) {
                    items(state.ancestors, key = { "a-${it.id}" }) { status ->
                        StatusItem(
                            status = status,
                            myUserId = state.myUserId,
                            onReply = onReply,
                            onFavourite = viewModel::onFavourite,
                            onBoost = viewModel::onBoost,
                            onBookmark = viewModel::onBookmark,
                            onDelete = viewModel::onDelete,
                            onEdit = viewModel::prepareEdit,
                            onQuote = viewModel::prepareQuote,
                            onOpenThread = onOpenThread,
                            onOpenProfile = onOpenProfile,
                            onOpenMedia = onOpenMedia,
                            onOpenLink = onOpenLink,
                        )
                    }
                    item(key = "t-${state.target!!.id}") {
                        StatusItem(
                            status = state.target!!,
                            myUserId = state.myUserId,
                            highlighted = true,
                            onReply = onReply,
                            onFavourite = viewModel::onFavourite,
                            onBoost = viewModel::onBoost,
                            onBookmark = viewModel::onBookmark,
                            onDelete = viewModel::onDelete,
                            onEdit = viewModel::prepareEdit,
                            onQuote = viewModel::prepareQuote,
                            onOpenThread = { /* already viewing */ },
                            onOpenProfile = onOpenProfile,
                            onOpenMedia = onOpenMedia,
                            onOpenLink = onOpenLink,
                        )
                    }
                    item(key = "__div__") { HorizontalDivider() }
                    items(state.descendants, key = { "d-${it.id}" }) { status ->
                        StatusItem(
                            status = status,
                            myUserId = state.myUserId,
                            onReply = onReply,
                            onFavourite = viewModel::onFavourite,
                            onBoost = viewModel::onBoost,
                            onBookmark = viewModel::onBookmark,
                            onDelete = viewModel::onDelete,
                            onEdit = viewModel::prepareEdit,
                            onQuote = viewModel::prepareQuote,
                            onOpenThread = onOpenThread,
                            onOpenProfile = onOpenProfile,
                            onOpenMedia = onOpenMedia,
                            onOpenLink = onOpenLink,
                        )
                    }
                }
            }
        }
    }
}
