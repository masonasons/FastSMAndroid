package me.masonasons.fastsm.domain.model

/**
 * A poll attached to an outgoing post. Mastodon-only today.
 *
 *  [options] 2..4 options.
 *  [expiresInSec] seconds until the poll closes.
 *  [multiple] lets voters pick more than one option.
 *  [hideTotals] hides tallies until the poll ends.
 */
data class PollSpec(
    val options: List<String>,
    val expiresInSec: Int,
    val multiple: Boolean,
    val hideTotals: Boolean,
)
