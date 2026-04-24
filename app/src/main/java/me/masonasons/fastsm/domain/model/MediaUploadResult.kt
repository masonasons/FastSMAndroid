package me.masonasons.fastsm.domain.model

/**
 * Result of uploading a single media attachment to a platform.
 * [mediaId] is the server's id, to include in a subsequent post request.
 * Mastodon v2 may still be processing the attachment when this returns;
 * the server will accept the id in a post regardless.
 */
data class MediaUploadResult(
    val mediaId: String,
    val previewUrl: String?,
    val processing: Boolean,
)
