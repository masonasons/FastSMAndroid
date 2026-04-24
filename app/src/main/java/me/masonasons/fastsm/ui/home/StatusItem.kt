package me.masonasons.fastsm.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import me.masonasons.fastsm.domain.model.PlatformType
import me.masonasons.fastsm.domain.model.PostAction
import me.masonasons.fastsm.domain.model.UniversalMedia
import me.masonasons.fastsm.domain.model.UniversalStatus
import me.masonasons.fastsm.domain.model.UniversalUser
import me.masonasons.fastsm.domain.template.Demojify
import me.masonasons.fastsm.domain.template.PostTemplateRenderer
import me.masonasons.fastsm.domain.template.TemplateConfig
import java.time.Duration
import java.time.Instant

/**
 * A timeline item. The visual layout is deliberately simple; the primary
 * interaction surface is the TalkBack custom-action menu exposed via
 * [Modifier.semantics], which mirrors the desktop FastSM's per-item hotkeys.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StatusItem(
    status: UniversalStatus,
    onReply: (UniversalStatus) -> Unit,
    onFavourite: (UniversalStatus) -> Unit,
    onBoost: (UniversalStatus) -> Unit,
    onBookmark: (UniversalStatus) -> Unit,
    onOpenThread: (UniversalStatus) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenMedia: (UniversalMedia) -> Unit,
    onOpenLink: (String) -> Unit,
    onDelete: (UniversalStatus) -> Unit,
    myUserId: String?,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onEdit: (UniversalStatus) -> Unit = {},
    onQuote: (UniversalStatus) -> Unit = {},
) {
    val templates = LocalPostTemplates.current
    val config = templates.config
    val display = status.reblog ?: status
    val boosterLine = status.reblog?.let {
        "Boosted by ${displayNameFor(status.account, config)} (@${status.account.acct})"
    }
    val isOwn = myUserId != null && display.account.id == myUserId
    val header = buildString {
        append(displayNameFor(display.account, config))
        append(" (@").append(display.account.acct).append(")")
    }
    val cw = display.spoilerText?.takeIf { it.isNotBlank() }
    val body = display.text.ifBlank { "(no text content)" }
    val meta = buildString {
        append(formatRelative(display.createdAt))
        if (display.visibility != null) append(" \u00b7 ").append(display.visibility.label)
        if (display.repliesCount > 0) append(" \u00b7 ").append(display.repliesCount).append(" replies")
        if (display.boostsCount > 0) append(" \u00b7 ").append(display.boostsCount).append(" boosts")
        if (display.favouritesCount > 0) append(" \u00b7 ").append(display.favouritesCount).append(" favourites")
    }

    // Render via the user-editable template. The template renderer handles CW
    // folding (per cwMode), media-description appending, display-name demojify,
    // and leading-mention collapse — all driven by TemplateConfig. Only the
    // bits the template does NOT know about (quotes, link counts, engagement
    // state) are appended as suffixes for TalkBack.
    val templateString = if (status.reblog != null) templates.boost else templates.post
    val rendered = PostTemplateRenderer.renderStatus(templateString, status, config)
    val quote = display.quote
    val spokenDescription = buildString {
        append(rendered)
        if (quote != null) {
            append(". Quoting @").append(quote.account.acct).append(": ").append(quote.text)
        }
        if (display.links.isNotEmpty()) {
            append(". ").append(display.links.size).append(" link")
            if (display.links.size != 1) append("s")
        }
        if (display.favourited) append(". Favourited")
        if (display.boosted) append(". Boosted")
        if (display.bookmarked) append(". Bookmarked")
    }

    // User-configurable: Settings → Post actions controls which of these
    // appear. Build a single [MenuAction] list so the same set drives both
    // TalkBack's customActions and the visible long-press DropdownMenu.
    val enabled = LocalEnabledPostActions.current
    val menuActions = buildList {
        if (PostAction.REPLY in enabled) {
            add(MenuAction("Reply") { onReply(display) })
        }
        if (PostAction.QUOTE in enabled) {
            add(MenuAction("Quote") { onQuote(display) })
        }
        if (PostAction.FAVOURITE in enabled) {
            add(
                MenuAction(if (display.favourited) "Unfavourite" else "Favourite") { onFavourite(display) }
            )
        }
        if (PostAction.BOOST in enabled) {
            add(MenuAction(if (display.boosted) "Unboost" else "Boost") { onBoost(display) })
        }
        if (PostAction.BOOKMARK in enabled) {
            add(
                MenuAction(if (display.bookmarked) "Remove bookmark" else "Bookmark") { onBookmark(display) }
            )
        }
        if (PostAction.VIEW_PROFILE in enabled) {
            // One "View profile" action per unique user in the post — author plus
            // any @-mentioned users. Deduped by server ID so the author (usually
            // also in mentions on replies) doesn't appear twice.
            val profileTargets = buildList {
                add(display.account.id to display.account.acct)
                display.mentions.forEach { add(it.id to it.acct) }
            }.distinctBy { it.first }
            profileTargets.forEach { (userId, acct) ->
                add(MenuAction("View profile of @${acct.removePrefix("@")}") { onOpenProfile(userId) })
            }
        }
        if (PostAction.VIEW_MEDIA in enabled) {
            display.mediaAttachments.forEach { m ->
                val desc = m.description?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
                add(MenuAction("View ${mediaLabel(m.type)}$desc") { onOpenMedia(m) })
            }
        }
        if (PostAction.OPEN_LINK in enabled) {
            display.links.forEach { url ->
                add(MenuAction("Open link: ${linkLabel(url)}") { onOpenLink(url) })
            }
        }
        if (isOwn && display.platform == PlatformType.MASTODON && PostAction.EDIT in enabled) {
            add(MenuAction("Edit") { onEdit(display) })
        }
        if (isOwn && PostAction.DELETE in enabled) {
            add(MenuAction("Delete") { onDelete(display) })
        }
    }
    val actions = menuActions.toAccessibilityActions()
    var menuOpen by remember { mutableStateOf(false) }

    // clearAndSetSemantics (not semantics + mergeDescendants) so TalkBack reads
    // the composed `spokenDescription` exactly once instead of concatenating it
    // with each child Text. It also wipes the click semantic from .clickable, so
    // we re-add `onClick` below; the touch+ripple handling from .clickable stays.
    val base = modifier
        .fillMaxWidth()
        .then(
            // Tap opens the thread; long-press opens the context menu mirroring
            // the TalkBack customActions. Highlighted items (the thread target)
            // aren't re-opened on tap to avoid a self-navigation loop.
            if (!highlighted) Modifier.combinedClickable(
                onClick = { onOpenThread(display) },
                onLongClick = { menuOpen = true },
            ) else Modifier.combinedClickable(
                onClick = { /* no-op; already viewing */ },
                onLongClick = { menuOpen = true },
            )
        )
    val themed = if (highlighted) base.background(MaterialTheme.colorScheme.primaryContainer) else base
    val surface = themed.clearAndSetSemantics {
        contentDescription = spokenDescription
        customActions = actions
        if (!highlighted) onClick { onOpenThread(display); true }
    }

    Column(
        modifier = surface.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (boosterLine != null) {
            Text(boosterLine, style = MaterialTheme.typography.labelLarge)
        }
        Text(header, style = MaterialTheme.typography.labelLarge)
        if (cw != null) {
            Text("CW: $cw", style = MaterialTheme.typography.bodyLarge)
        }
        Text(body, style = MaterialTheme.typography.bodyLarge)
        if (quote != null) {
            QuotedPostBlock(quote = quote, config = config, onOpen = { onOpenThread(quote) })
        }
        if (display.mediaAttachments.isNotEmpty()) {
            MediaAttachmentList(
                media = display.mediaAttachments,
                onOpenMedia = onOpenMedia,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(meta, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // DropdownMenu anchors to the previous sibling's bounds, so placing
        // it at the end of the Column parents it to the whole post. Empty
        // menu means no actions enabled — skip rendering entirely.
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

/**
 * Inline preview of the post a status quotes. Tappable — opens the quoted
 * post's thread. clearAndSetSemantics wraps the whole block in a single
 * TalkBack node so it reads cleanly as part of the parent post's
 * announcement, rather than fragmenting into the parent's text + quoted
 * author + quoted body as separate swipes.
 */
@Composable
private fun QuotedPostBlock(quote: UniversalStatus, config: TemplateConfig, onOpen: () -> Unit) {
    val quoteName = displayNameFor(quote.account, config)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable { onOpen() }
            .padding(12.dp)
            .clearAndSetSemantics {
                contentDescription = buildString {
                    append("Quoting ").append(quoteName)
                    append(" (@").append(quote.account.acct).append("): ")
                    append(quote.text)
                }
                onClick { onOpen(); true }
            },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "Quoting @${quote.account.acct}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(quote.text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Apply the user's demojify setting to a display name before it hits the
 *  visible Text or a hand-rolled `spokenDescription`. The template renderer
 *  applies the same transform when it resolves `$account.display_name$`, so
 *  sighted and TalkBack users see matching strings. */
internal fun displayNameFor(user: UniversalUser, config: TemplateConfig): String {
    val raw = user.displayName
    if (!config.demojifyDisplayNames) return raw
    return Demojify.demojify(raw).ifBlank { user.acct }
}

internal fun mediaLabel(type: String): String = when (type) {
    "image" -> "image"
    "gifv" -> "animated image"
    "video" -> "video"
    "audio" -> "audio"
    else -> "attachment"
}

/** Short label for a URL — prefers the host so TalkBack action names stay readable. */
internal fun linkLabel(url: String): String = runCatching {
    val u = java.net.URI(url)
    u.host?.removePrefix("www.") ?: url
}.getOrDefault(url)

private fun formatRelative(instant: Instant): String {
    val now = Instant.now()
    val d = Duration.between(instant, now)
    val seconds = d.seconds
    return when {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86_400 -> "${seconds / 3600}h"
        seconds < 604_800 -> "${seconds / 86_400}d"
        else -> "${seconds / 604_800}w"
    }
}
