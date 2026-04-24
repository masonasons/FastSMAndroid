package me.masonasons.fastsm.domain.model

enum class Visibility(val wire: String, val label: String) {
    PUBLIC("public", "Public"),
    UNLISTED("unlisted", "Unlisted"),
    PRIVATE("private", "Followers only"),
    DIRECT("direct", "Direct");

    companion object {
        fun fromWire(value: String?): Visibility? = when (value) {
            "public" -> PUBLIC
            "unlisted" -> UNLISTED
            "private" -> PRIVATE
            "direct" -> DIRECT
            else -> null
        }
    }
}
