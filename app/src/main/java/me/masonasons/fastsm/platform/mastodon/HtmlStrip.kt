package me.masonasons.fastsm.platform.mastodon

import android.text.Spanned
import android.text.style.URLSpan
import androidx.core.text.HtmlCompat

/**
 * Strip Mastodon status HTML to a clean plain-text body suitable for
 * screen-reader consumption. Preserves paragraph and line breaks.
 */
object HtmlStrip {
    fun toPlainText(html: String): String {
        if (html.isBlank()) return ""
        val spanned: Spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
        return spanned.toString().trim()
    }

    private val leadingReRegex = Regex("""^(RE|QT|re|qt):\s*https?://\S+\s*""")
    private val trailingStatusUrlRegex = Regex("""\s*https?://[^\s]+/@[^\s]+/\d+\s*$""")

    /**
     * When a post carries a quote, strip the vestigial "RE: <url>" prefix /
     * trailing status URL that older Mastodon clients and cross-posters used
     * before the native quote API. Leaves non-quote posts untouched.
     *
     *  [quoteUrl] is the canonical URL of the quoted post; if the text ends
     *  with it (common in pre-4.4 compat posts), strip that too.
     */
    fun stripQuoteMarkers(text: String, quoteUrl: String?): String {
        if (text.isBlank()) return text
        var result = leadingReRegex.replace(text, "").trim()
        if (!quoteUrl.isNullOrBlank() && result.endsWith(quoteUrl)) {
            result = result.substring(0, result.length - quoteUrl.length).trimEnd()
        }
        result = trailingStatusUrlRegex.replace(result, "").trim()
        return result
    }

    /**
     * Extract external URLs from Mastodon status HTML. Combines two strategies
     * for robustness across Mastodon forks and HTML quirks:
     *
     *  1. [URLSpan]s from Android's HTML parser — filter by visible text
     *     starting with `@` (mentions) or `#` (hashtags).
     *  2. A direct `href="http..."` regex over the raw HTML — filter URLs that
     *     look like Mastodon mention or hashtag routes (`/@…`, `/tags/…`).
     */
    fun extractLinks(html: String): List<String> {
        if (html.isBlank()) return emptyList()
        return (extractViaUrlSpans(html) + extractViaHrefRegex(html)).distinct()
    }

    private fun extractViaUrlSpans(html: String): List<String> = runCatching {
        val spanned: Spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
        spanned.getSpans(0, spanned.length, URLSpan::class.java)
            .mapNotNull { span ->
                val start = spanned.getSpanStart(span)
                val end = spanned.getSpanEnd(span)
                val text = spanned.subSequence(start, end).toString().trim()
                if (text.startsWith("@") || text.startsWith("#")) null else span.url
            }
    }.getOrDefault(emptyList())

    private val hrefRegex = Regex("""href=["'](https?://[^"']+)["']""")

    private fun extractViaHrefRegex(html: String): List<String> =
        hrefRegex.findAll(html).mapNotNull { match ->
            val url = match.groupValues[1]
            // Path-based heuristic: Mastodon mention URLs contain `/@user`
            // and hashtag URLs contain `/tags/foo`.
            if (url.contains("/@") || url.contains("/tags/")) null else url
        }.toList()
}
