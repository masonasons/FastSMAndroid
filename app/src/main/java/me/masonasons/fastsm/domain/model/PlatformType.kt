package me.masonasons.fastsm.domain.model

enum class PlatformType(val wire: String, val displayName: String) {
    MASTODON("mastodon", "Mastodon"),
    BLUESKY("bluesky", "Bluesky");

    companion object {
        fun fromWire(value: String): PlatformType =
            entries.firstOrNull { it.wire == value }
                ?: error("Unknown platform: $value")
    }
}
