package me.masonasons.fastsm.domain.model

/** Raw-text source of a post, for prefilling an edit compose. */
data class StatusSource(
    val text: String,
    val spoilerText: String,
)
