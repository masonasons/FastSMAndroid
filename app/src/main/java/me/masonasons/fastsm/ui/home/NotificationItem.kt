package me.masonasons.fastsm.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.unit.dp
import me.masonasons.fastsm.domain.model.NotificationType
import me.masonasons.fastsm.domain.model.PlatformType
import me.masonasons.fastsm.domain.model.PostAction
import me.masonasons.fastsm.domain.model.UniversalMedia
import me.masonasons.fastsm.domain.model.UniversalNotification
import me.masonasons.fastsm.domain.model.UniversalStatus
import me.masonasons.fastsm.domain.template.PostTemplateRenderer
import me.masonasons.fastsm.domain.template.TemplateConfig
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotificationItem(
    notification: UniversalNotification,
    myUserId: String?,
    onReply: (UniversalStatus) -> Unit,
    onFavourite: (UniversalStatus) -> Unit,
    onBoost: (UniversalStatus) -> Unit,
    onBookmark: (UniversalStatus) -> Unit,
    onDelete: (UniversalStatus) -> Unit,
    onOpenThread: (UniversalStatus) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenMedia: (UniversalMedia) -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    onEdit: (UniversalStatus) -> Unit = {},
    onQuote: (UniversalStatus) -> Unit = {},
) {
    val templates = LocalPostTemplates.current
    val config = templates.config
    val header = headerLine(notification, config)
    val status = notification.status
    val body = status?.text?.ifBlank { null }
    val meta = formatRelative(notification.createdAt)

    // The template renderer handles CW folding (per cwMode) and demojify —
    // desktop does NOT auto-append media descriptions to notifications, so
    // we don't either. Only link count + engagement state gets appended.
    val rendered = PostTemplateRenderer.renderNotification(templates.notification, notification, config)
    val spoken = buildString {
        append(rendered)
        if (status?.links?.isNotEmpty() == true) {
            append(". ").append(status.links.size).append(" link")
            if (status.links.size != 1) append("s")
        }
        if (status != null) {
            if (status.favourited) append(". Favourited")
            if (status.boosted) append(". Boosted")
            if (status.bookmarked) append(". Bookmarked")
        }
    }

    val enabled = LocalEnabledPostActions.current
    val menuActions = buildList {
        if (PostAction.VIEW_PROFILE in enabled) {
            val profileTargets = buildList {
                add(notification.account.id to notification.account.acct)
                status?.account?.let { add(it.id to it.acct) }
                status?.mentions?.forEach { add(it.id to it.acct) }
            }.distinctBy { it.first }
            profileTargets.forEach { (userId, acct) ->
                add(MenuAction("View profile of @${acct.removePrefix("@")}") { onOpenProfile(userId) })
            }
        }
        if (status != null) {
            val isMention = notification.type == NotificationType.MENTION ||
                notification.type == NotificationType.REPLY ||
                notification.type == NotificationType.QUOTE
            if (isMention) {
                if (PostAction.REPLY in enabled) {
                    add(MenuAction("Reply") { onReply(status) })
                }
                if (PostAction.QUOTE in enabled) {
                    add(MenuAction("Quote") { onQuote(status) })
                }
                if (PostAction.FAVOURITE in enabled) {
                    add(MenuAction(if (status.favourited) "Unfavourite" else "Favourite") { onFavourite(status) })
                }
                if (PostAction.BOOST in enabled) {
                    add(MenuAction(if (status.boosted) "Unboost" else "Boost") { onBoost(status) })
                }
                if (PostAction.BOOKMARK in enabled) {
                    add(MenuAction(if (status.bookmarked) "Remove bookmark" else "Bookmark") { onBookmark(status) })
                }
            }
            if (myUserId != null && status.account.id == myUserId) {
                if (status.platform == PlatformType.MASTODON && PostAction.EDIT in enabled) {
                    add(MenuAction("Edit post") { onEdit(status) })
                }
                if (PostAction.DELETE in enabled) {
                    add(MenuAction("Delete post") { onDelete(status) })
                }
            }
            if (PostAction.VIEW_MEDIA in enabled) {
                status.mediaAttachments.forEach { m ->
                    val desc = m.description?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
                    add(MenuAction("View ${mediaLabel(m.type)}$desc") { onOpenMedia(m) })
                }
            }
            if (PostAction.OPEN_LINK in enabled) {
                status.links.forEach { url ->
                    add(MenuAction("Open link: ${linkLabel(url)}") { onOpenLink(url) })
                }
            }
        }
    }
    val actions = menuActions.toAccessibilityActions()
    var menuOpen by remember { mutableStateOf(false) }

    // See StatusItem for why this is clearAndSetSemantics + re-added onClick.
    // Tap opens the thread; long-press opens the same action menu a TalkBack
    // user would see via custom actions.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (status != null) onOpenThread(status) },
                onLongClick = { if (menuActions.isNotEmpty()) menuOpen = true },
            )
            .clearAndSetSemantics {
                contentDescription = spoken
                customActions = actions
                if (status != null) onClick { onOpenThread(status); true }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(header, style = MaterialTheme.typography.labelLarge)
        if (status?.spoilerText?.isNotBlank() == true) {
            Text("CW: ${status.spoilerText}", style = MaterialTheme.typography.bodyLarge)
        }
        if (body != null) {
            Text(body, style = MaterialTheme.typography.bodyLarge)
        }
        if (status?.mediaAttachments?.isNotEmpty() == true) {
            MediaAttachmentList(media = status.mediaAttachments, onOpenMedia = onOpenMedia)
        }
        if (status?.links?.isNotEmpty() == true) {
            LinkList(links = status.links, onOpenLink = onOpenLink)
        }
        Text(
            meta,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (menuActions.isNotEmpty()) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                menuActions.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action.label) },
                        onClick = { action.run(); menuOpen = false },
                    )
                }
            }
        }
    }
    HorizontalDivider()
}

private fun headerLine(n: UniversalNotification, config: TemplateConfig): String {
    val who = "${displayNameFor(n.account, config)} (@${n.account.acct})"
    return when (n.type) {
        NotificationType.MENTION -> "$who mentioned you"
        NotificationType.REPLY -> "$who replied to your post"
        NotificationType.QUOTE -> "$who quoted your post"
        NotificationType.REBLOG -> "$who boosted your post"
        NotificationType.FAVOURITE -> "$who favourited your post"
        NotificationType.FOLLOW -> "$who followed you"
        NotificationType.FOLLOW_REQUEST -> "$who requested to follow you"
        NotificationType.POLL -> "Poll from $who ended"
        NotificationType.UPDATE -> "$who edited a post"
        NotificationType.STATUS -> "$who posted"
        NotificationType.OTHER -> "$who: ${n.typeRaw}"
    }
}

private fun formatRelative(instant: Instant): String {
    val d = Duration.between(instant, Instant.now())
    val seconds = d.seconds
    return when {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86_400 -> "${seconds / 3600}h"
        seconds < 604_800 -> "${seconds / 86_400}d"
        else -> "${seconds / 604_800}w"
    }
}
