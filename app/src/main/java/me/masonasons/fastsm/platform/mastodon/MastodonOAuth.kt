package me.masonasons.fastsm.platform.mastodon

import android.net.Uri

/**
 * Mastodon OAuth 2.0 authorization-code flow helpers.
 *
 * Flow:
 *  1. POST /api/v1/apps with redirect_uri + scopes → client_id, client_secret
 *  2. Launch [buildAuthorizeUrl] in a Custom Tab
 *  3. User approves → server redirects to `fastsm://oauth?code=...`
 *  4. Main activity catches the deep link; pull code via [extractCode]
 *  5. POST /oauth/token → access token
 */
object MastodonOAuth {
    const val SCOPES = "read write follow push"
    const val REDIRECT_URI = "fastsm://oauth"
    const val CLIENT_NAME = "FastSM"
    const val WEBSITE = "https://github.com/masonasons/FastSM"

    fun normalizeInstance(raw: String): String {
        val trimmed = raw.trim().removeSuffix("/")
        return when {
            trimmed.isEmpty() -> trimmed
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "https://$trimmed"
        }
    }

    fun buildAuthorizeUrl(instanceBase: String, clientId: String): Uri =
        Uri.parse("$instanceBase/oauth/authorize").buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", SCOPES)
            .build()

    fun extractCode(uri: Uri): String? = uri.getQueryParameter("code")
    fun extractError(uri: Uri): String? = uri.getQueryParameter("error")
}
