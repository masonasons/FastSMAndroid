package me.masonasons.fastsm.domain.template

import me.masonasons.fastsm.domain.model.NotificationType
import me.masonasons.fastsm.domain.model.UniversalMedia
import me.masonasons.fastsm.domain.model.UniversalNotification
import me.masonasons.fastsm.domain.model.UniversalStatus
import me.masonasons.fastsm.domain.model.UniversalUser
import java.time.Duration
import java.time.Instant

/**
 * Expands `$placeholder$` and `$object.attribute$` tokens in a user-supplied
 * template string against a [UniversalStatus] or [UniversalNotification].
 *
 * Syntax mirrors desktop FastSM so soundpack-style templates port directly:
 *
 *  - `$text$`, `$spoiler_text$`, `$url$`, `$visibility$`, `$created_at$`
 *  - `$favourites_count$`, `$boosts_count$`, `$replies_count$`
 *  - `$account.display_name$`, `$account.acct$`, `$account.username$`, `$account.url$`
 *  - `$reblog.<any post field>$` for boost-only templates
 *  - On notifications: `$type$` expands to a human label ("mentioned you" etc.)
 *
 * Unknown tokens resolve to empty string — matching the desktop's forgiving
 * behavior. `$text$` is substituted last so a template variable appearing
 * inside post text can't be reinterpreted.
 *
 * A [TemplateConfig] controls the desktop-parity post-processing hooks:
 * content-warning mode, media-description appending, display-name
 * demojification, and leading-mention collapse. Defaults match desktop out
 * of the box so templates behave the same as application.py.
 */
object PostTemplateRenderer {

    // Defaults lifted verbatim from c:\stuff\FastSM\application.py:119–126.
    const val DEFAULT_POST = "\$account.display_name\$ (@\$account.acct\$): \$text\$ \$created_at\$"
    const val DEFAULT_BOOST = "\$account.display_name\$ boosted \$reblog.account.display_name\$: \$text\$ \$created_at\$"
    const val DEFAULT_NOTIFICATION = "\$account.display_name\$ (@\$account.acct\$) \$type\$: \$text\$ \$created_at\$"

    private val tokenRegex = Regex("""\$([a-zA-Z_][a-zA-Z_0-9.]*)\$""")
    private const val TEXT_SENTINEL = "@@FASTSM_TEXT@@"

    // Matches one or more leading "@handle " tokens. Used by the
    // max-usernames-display collapser.
    private val leadingMentions = Regex("^((?:@\\S+\\s+)+)")

    fun renderStatus(
        template: String,
        status: UniversalStatus,
        config: TemplateConfig = TemplateConfig(),
    ): String {
        // Boosts and regular posts share the same resolver — caller picks the
        // template string; the resolver looks at $reblog.*$ when needed.
        val display = status.reblog ?: status
        val cookedText = cookStatusText(display, config, includeMedia = true)
        // Defer $text$ expansion so tokens inside the body can't recurse.
        val deferred = template.replace("\$text\$", TEXT_SENTINEL)
        val expanded = tokenRegex.replace(deferred) { m ->
            resolveStatusToken(m.groupValues[1], status, display, config).orEmpty()
        }
        return expanded.replace(TEXT_SENTINEL, cookedText).trim()
    }

    fun renderNotification(
        template: String,
        notification: UniversalNotification,
        config: TemplateConfig = TemplateConfig(),
    ): String {
        val status = notification.status
        // Desktop does NOT auto-append media descriptions to notification text
        // (application.py:process_notification), so pass includeMedia = false.
        val cookedText = status?.let { cookStatusText(it, config, includeMedia = false) }.orEmpty()
        // Defer $text$ and $status.text$ to avoid recursion through post body.
        val deferred = template
            .replace("\$text\$", TEXT_SENTINEL)
            .replace("\$status.text\$", TEXT_SENTINEL)
        val expanded = tokenRegex.replace(deferred) { m ->
            resolveNotificationToken(m.groupValues[1], notification, config).orEmpty()
        }
        return expanded.replace(TEXT_SENTINEL, cookedText).trim()
    }

    /**
     * Build the final `$text$` value. Mirrors desktop's `process_status`
     * sequence: collapse leading mentions, prepend CW per [TemplateConfig.cwMode],
     * then optionally append media descriptions.
     */
    private fun cookStatusText(
        status: UniversalStatus,
        config: TemplateConfig,
        includeMedia: Boolean,
    ): String {
        var text = status.text
        if (config.maxUsernamesDisplay > 0) {
            text = collapseLeadingMentions(text, config.maxUsernamesDisplay)
        }
        val cw = status.spoilerText?.takeIf { it.isNotBlank() }
        if (cw != null) {
            text = when (config.cwMode) {
                CwMode.HIDE -> "CW: $cw"
                CwMode.SHOW -> if (text.isNotBlank()) "CW: $cw. $text" else "CW: $cw"
                CwMode.IGNORE -> text
            }
        }
        if (includeMedia && config.includeMediaDescriptions) {
            status.mediaAttachments.forEach { m ->
                text += formatMediaSuffix(m)
            }
        }
        return text
    }

    private fun collapseLeadingMentions(text: String, threshold: Int): String {
        val match = leadingMentions.find(text) ?: return text
        val section = match.groupValues[1]
        val handles = section.trim().split(Regex("\\s+"))
        if (handles.size <= threshold) return text
        val rest = text.substring(section.length).trimStart()
        val remaining = handles.size - 1
        val head = "${handles[0]} and $remaining more"
        return if (rest.isEmpty()) head else "$head $rest"
    }

    private fun formatMediaSuffix(m: UniversalMedia): String {
        val label = mediaTypeLabel(m.type)
        val desc = m.description?.takeIf { it.isNotBlank() }
        return if (desc != null) " ($label) description: $desc" else " ($label) with no description"
    }

    private fun mediaTypeLabel(type: String): String = when (type) {
        "gifv" -> "GIFV"
        else -> type.uppercase()
    }

    /**
     * Resolve a `$...$` token against a status.
     *
     *  [root] is the top-level status as stored in the timeline — on boosts
     *  the wrapper status with the booster as `account`.
     *  [display] is the effective post — `root.reblog` on a boost, else [root].
     */
    private fun resolveStatusToken(
        path: String,
        root: UniversalStatus,
        display: UniversalStatus,
        config: TemplateConfig,
    ): String? {
        val parts = path.split(".")
        return when (parts[0]) {
            // On boosts, $account$ refers to the BOOSTER (matches desktop).
            "account" -> resolveAccount(parts.drop(1), root.account, config)
            "reblog" -> root.reblog?.let {
                resolveBareStatus(parts.drop(1), it, config)
            }
            // Everything else is read from the effective post (the one with
            // the body text and metadata worth announcing).
            else -> resolveBareStatus(parts, display, config)
        }
    }

    private fun resolveBareStatus(
        path: List<String>,
        status: UniversalStatus,
        config: TemplateConfig,
    ): String? =
        when (path.firstOrNull()) {
            null, "" -> "${maybeDemojify(status.account.displayName, config)} (@${status.account.acct})"
            "text" -> status.text
            "spoiler_text" -> status.spoilerText
            "url" -> status.url
            "visibility" -> status.visibility?.label
            "created_at" -> formatRelative(status.createdAt)
            "favourites_count" -> status.favouritesCount.toString()
            "boosts_count" -> status.boostsCount.toString()
            "replies_count" -> status.repliesCount.toString()
            "account" -> resolveAccount(path.drop(1), status.account, config)
            else -> null
        }

    private fun resolveAccount(
        path: List<String>,
        user: UniversalUser,
        config: TemplateConfig,
    ): String? =
        when (path.firstOrNull()) {
            null, "" -> "${maybeDemojify(user.displayName, config)} (@${user.acct})"
            "display_name" -> maybeDemojify(user.displayName, config).ifBlank { user.acct }
            "acct" -> user.acct
            "username" -> user.username
            "url" -> user.url
            else -> null
        }

    private fun maybeDemojify(input: String, config: TemplateConfig): String =
        if (config.demojifyDisplayNames) Demojify.demojify(input) else input

    private fun resolveNotificationToken(
        path: String,
        notification: UniversalNotification,
        config: TemplateConfig,
    ): String? {
        val parts = path.split(".")
        return when (parts[0]) {
            "type" -> notificationTypeLabel(notification.type)
            "account" -> resolveAccount(parts.drop(1), notification.account, config)
            "status" -> notification.status?.let { resolveBareStatus(parts.drop(1), it, config) }
            "created_at" -> formatRelative(notification.createdAt)
            // Convenience: bare $text$ inside a notification template pulls
            // from the related post when there is one.
            "text" -> notification.status?.text
            else -> null
        }
    }

    private fun notificationTypeLabel(type: NotificationType): String = when (type) {
        NotificationType.MENTION -> "mentioned you"
        NotificationType.REPLY -> "replied to your post"
        NotificationType.QUOTE -> "quoted your post"
        NotificationType.REBLOG -> "boosted your post"
        NotificationType.FAVOURITE -> "favourited your post"
        NotificationType.FOLLOW -> "followed you"
        NotificationType.FOLLOW_REQUEST -> "requested to follow you"
        NotificationType.POLL -> "poll ended"
        NotificationType.UPDATE -> "edited a post"
        NotificationType.STATUS -> "posted"
        NotificationType.OTHER -> "notification"
    }

    private fun formatRelative(instant: Instant): String {
        val seconds = Duration.between(instant, Instant.now()).seconds
        return when {
            seconds < 60 -> "just now"
            seconds < 3600 -> "${seconds / 60} minutes ago"
            seconds < 86_400 -> "${seconds / 3600} hours ago"
            seconds < 604_800 -> "${seconds / 86_400} days ago"
            else -> "${seconds / 604_800} weeks ago"
        }
    }
}

/** Runtime-adjustable knobs that control desktop-parity text post-processing. */
data class TemplateConfig(
    val cwMode: CwMode = CwMode.HIDE,
    val includeMediaDescriptions: Boolean = true,
    val demojifyDisplayNames: Boolean = false,
    /** 0 disables the collapser; otherwise, any consecutive leading @mentions
     *  past this threshold get rewritten to "@first and N more ...". */
    val maxUsernamesDisplay: Int = 0,
)

enum class CwMode(val key: String, val label: String) {
    HIDE("hide", "Hide post content behind CW"),
    SHOW("show", "Show CW and post content"),
    IGNORE("ignore", "Ignore CW, show post content");

    companion object {
        fun fromKey(key: String?): CwMode = entries.firstOrNull { it.key == key } ?: HIDE
    }
}
