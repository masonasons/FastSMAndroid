package me.masonasons.fastsm.platform.bluesky

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Extract @-mentions from post text and build an app.bsky.richtext.facet array.
 * Bluesky uses UTF-8 byte offsets — we compute them by encoding the prefix.
 *
 * Handles look like `alice.bsky.social` (domain-shaped), so we require at
 * least one dot in the part after the `@`. Handles that can't be resolved to
 * a DID are skipped silently; the raw @text still posts.
 */
internal object BlueskyFacets {
    private val mentionRegex = Regex("""(?<![\w.])@([a-zA-Z0-9][a-zA-Z0-9-]*(?:\.[a-zA-Z0-9-]+)+)""")

    suspend fun buildMentionFacets(
        api: BlueskyApi,
        text: String,
    ): JsonArray {
        val matches = mentionRegex.findAll(text).toList()
        if (matches.isEmpty()) return JsonArray(emptyList())
        val bytes = text.toByteArray(Charsets.UTF_8)
        // Char index → byte index table so we only encode once.
        val charToByte = IntArray(text.length + 1)
        var b = 0
        for (i in text.indices) {
            charToByte[i] = b
            b += text[i].toString().toByteArray(Charsets.UTF_8).size
        }
        charToByte[text.length] = b
        check(b == bytes.size) { "byte count mismatch" }

        return buildJsonArray {
            for (match in matches) {
                val handle = match.groupValues[1]
                val did = runCatching { api.resolveHandle(handle).did }.getOrNull() ?: continue
                val byteStart = charToByte[match.range.first]
                val byteEnd = charToByte[match.range.last + 1]
                addJsonObject {
                    putJsonObject("index") {
                        put("byteStart", byteStart)
                        put("byteEnd", byteEnd)
                    }
                    putJsonArray("features") {
                        addJsonObject {
                            put("\$type", "app.bsky.richtext.facet#mention")
                            put("did", did)
                        }
                    }
                }
            }
        }
    }
}
