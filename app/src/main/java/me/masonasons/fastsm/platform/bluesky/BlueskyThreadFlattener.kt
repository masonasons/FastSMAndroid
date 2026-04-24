package me.masonasons.fastsm.platform.bluesky

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.masonasons.fastsm.domain.model.StatusContext
import me.masonasons.fastsm.domain.model.UniversalStatus

/**
 * Flatten Bluesky's nested getPostThread tree into ancestors (parent chain
 * above the target) and descendants (replies below), to match the Mastodon
 * shape consumed by [me.masonasons.fastsm.ui.thread.ThreadViewModel].
 *
 * The thread element is a ThreadViewPost with nested `parent` (one ancestor)
 * and `replies` (a list of child ThreadViewPosts). We chain parents up and
 * flatten replies depth-first.
 */
internal object BlueskyThreadFlattener {
    private val jsonFormat = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    fun flatten(thread: JsonElement, rootUri: String): StatusContext {
        val ancestors = mutableListOf<UniversalStatus>()
        val descendants = mutableListOf<UniversalStatus>()
        val obj = thread as? JsonObject ?: return StatusContext(emptyList(), emptyList())

        // Walk up parent chain. parent may recursively contain its own parent.
        var parent = obj["parent"] as? JsonObject
        while (parent != null) {
            parent.toStatus()?.let { ancestors.add(0, it) }
            parent = parent["parent"] as? JsonObject
        }

        // Walk down replies breadth-first.
        val queue = ArrayDeque<JsonObject>()
        (obj["replies"] as? JsonArray)?.forEach { el ->
            (el as? JsonObject)?.let { queue.add(it) }
        }
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            node.toStatus()?.let { descendants.add(it) }
            (node["replies"] as? JsonArray)?.forEach { el ->
                (el as? JsonObject)?.let { queue.add(it) }
            }
        }

        return StatusContext(ancestors, descendants)
    }

    private fun JsonObject.toStatus(): UniversalStatus? {
        val post = this["post"] as? JsonObject ?: return null
        val dto = runCatching {
            jsonFormat.decodeFromJsonElement(BskyPostViewDto.serializer(), post)
        }.getOrNull() ?: return null
        return dto.toUniversal()
    }
}
