package me.masonasons.fastsm.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.masonasons.fastsm.data.db.dao.TimelineDao
import me.masonasons.fastsm.data.db.entity.TimelineEntity
import me.masonasons.fastsm.domain.model.TimelineSpec

class TimelineRepository(private val dao: TimelineDao) {

    fun observe(accountId: Long): Flow<List<TimelineSpec>> =
        dao.observeByAccount(accountId).map { list -> list.mapNotNull { it.toSpec() } }

    suspend fun get(accountId: Long): List<TimelineSpec> =
        dao.getByAccount(accountId).mapNotNull { it.toSpec() }

    suspend fun add(accountId: Long, spec: TimelineSpec) {
        if (!spec.closable) return
        val existing = dao.getByAccount(accountId)
        if (existing.any { it.toSpec() == spec }) return
        val nextPosition = (existing.maxOfOrNull { it.position } ?: 0) + 1
        dao.insert(spec.toEntity(accountId, nextPosition))
    }

    suspend fun remove(accountId: Long, spec: TimelineSpec) {
        if (!spec.closable) return
        dao.getByAccount(accountId).firstOrNull { it.toSpec() == spec }?.let { dao.delete(it.rowId) }
    }

    suspend fun clearForAccount(accountId: Long) = dao.deleteByAccount(accountId)

    private fun TimelineEntity.toSpec(): TimelineSpec? = when (kind) {
        "local" -> TimelineSpec.LocalPublic
        "federated" -> TimelineSpec.FederatedPublic
        "bookmarks" -> TimelineSpec.Bookmarks
        "favourites" -> TimelineSpec.Favourites
        "remote-instance" -> {
            val inst = instance ?: return null
            TimelineSpec.RemoteInstance(inst, localOnly ?: true)
        }
        "remote-user" -> {
            val inst = instance ?: return null
            val a = acct ?: return null
            TimelineSpec.RemoteUser(inst, a)
        }
        "user-posts" -> {
            val uid = userId ?: return null
            TimelineSpec.UserPosts(uid, label ?: uid)
        }
        "list" -> {
            // list id stored in userId column (a column reuse — still a string id).
            val listId = userId ?: return null
            TimelineSpec.UserList(listId, label ?: listId)
        }
        "hashtag" -> {
            // tag stored in acct column (no # prefix).
            val tag = acct ?: return null
            TimelineSpec.Hashtag(tag)
        }
        else -> null
    }

    private fun TimelineSpec.toEntity(accountId: Long, position: Int): TimelineEntity = when (this) {
        TimelineSpec.LocalPublic -> base("local", accountId, position)
        TimelineSpec.FederatedPublic -> base("federated", accountId, position)
        TimelineSpec.Bookmarks -> base("bookmarks", accountId, position)
        TimelineSpec.Favourites -> base("favourites", accountId, position)
        is TimelineSpec.RemoteInstance -> base("remote-instance", accountId, position).copy(
            instance = instance,
            localOnly = localOnly,
        )
        is TimelineSpec.RemoteUser -> base("remote-user", accountId, position).copy(
            instance = instance,
            acct = acct,
        )
        is TimelineSpec.UserPosts -> base("user-posts", accountId, position).copy(
            userId = userId,
            label = displayName,
        )
        is TimelineSpec.UserList -> base("list", accountId, position).copy(
            userId = listId,
            label = title,
        )
        is TimelineSpec.Hashtag -> base("hashtag", accountId, position).copy(
            acct = tag,
        )
        TimelineSpec.Home, TimelineSpec.Notifications ->
            error("Home and Notifications are implicit; do not persist")
    }

    private fun base(kind: String, accountId: Long, position: Int) = TimelineEntity(
        accountId = accountId,
        kind = kind,
        position = position,
        createdAt = System.currentTimeMillis(),
    )
}
