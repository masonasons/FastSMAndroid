package me.masonasons.fastsm.domain.template

/**
 * Strips emoji from a string — mirrors desktop FastSM's `demojify()` helper
 * (application.py:1640–1667). Used when the user turns on the "remove emojis
 * from display names" setting so TalkBack doesn't read out every 🌸 and :blobcat:
 * in a display name.
 *
 * Covers:
 *  - Mastodon-style custom emoji shortcodes: `:name:`
 *  - The main BMP symbol/dingbat range U+2600..U+27BF
 *  - Variation selectors and the zero-width joiner glue between composed emoji
 *  - Supplementary-plane emoji via surrogate-pair range U+1F000..U+1FBFF —
 *    covers Emoticons, Transport, Supplemental Symbols, and the newer
 *    Symbols & Pictographs Extended blocks in one sweep.
 */
object Demojify {

    private val shortcode = Regex(":[a-zA-Z0-9_+\\-]+:")
    private val unicodeEmoji = Regex(
        "[\\u2600-\\u27BF]" +                             // Misc Symbols, Dingbats
            "|[\\uFE00-\\uFE0F]" +                        // Variation selectors
            "|\\u200D" +                                   // Zero-width joiner
            "|[\\uD83C-\\uD83E][\\uDC00-\\uDFFF]"          // Supplementary-plane emoji
    )

    fun demojify(input: String): String {
        if (input.isEmpty()) return input
        return unicodeEmoji.replace(shortcode.replace(input, ""), "").trim()
    }
}
