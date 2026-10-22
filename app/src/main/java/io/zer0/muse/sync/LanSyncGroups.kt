// SPDX-License-Identifier: GPL-3.0-or-later
package io.zer0.muse.sync

import androidx.room.withTransaction
import io.zer0.muse.data.assistant.AssistantEntity
import io.zer0.muse.data.groupchat.GroupChatEntity
import io.zer0.muse.data.groupchat.GroupChatMessageEntity
import io.zer0.muse.data.session.MuseDb
import org.json.JSONArray
import org.json.JSONObject

/** Public room projection. Whispers, local attachments and member-private context never leave the phone. */
internal class LanSyncGroups(
    private val db: MuseDb,
    private val uuid: (String, String) -> String,
    private val native: (String, String) -> String,
    private val base: (String) -> JSONObject,
) {
    var skippedRooms: Int = 0
        private set
    private fun objects(a: JSONArray?): List<JSONObject> = if (a == null) emptyList() else (0 until a.length()).map { a.getJSONObject(it) }
    private fun strings(value: String): List<String> = JSONArray(value).let { a -> (0 until a.length()).map { a.getString(it) } }
    private fun array(values: List<JSONObject>) = JSONArray().also { a -> values.forEach { a.put(it) } }

    suspend fun export(assistants: List<AssistantEntity>): Map<String, JSONObject> {
        skippedRooms = 0
        val roles = assistants.associateBy { it.id }
        val result = linkedMapOf<String, JSONObject>()
        for (room in db.groupChatDao().getAll()) {
            val id = uuid("groupChats", room.id); val key = "chats:$id"; val body = base(key)
            val members = strings(room.memberIdsJson)
            val public = db.groupChatMessageDao().lanSyncByChat(room.id).filter {
                it.whisperTargetId == null && it.senderType in listOf("user", "assistant") && it.senderId != "system" && it.messageType != "system"
            }
            // Legacy rooms may have teams or departed speakers not representable on desktop.
            // Keep any acknowledged copy instead of interpreting omission as deletion.
            if (members.size !in 2..8 || members.distinct().size != members.size || members.any { it !in roles } ||
                public.any { it.senderType == "assistant" && it.senderId !in members }) {
                skippedRooms++
                if (body.optString("kind") == "group") result[key] = body
                continue
            }
            val oldMessages = objects(body.optJSONArray("messages")).associateBy { it.getString("id") }
            val messages = public.map { m ->
                val mid = uuid("groupMessage", m.id)
                val msg = oldMessages[mid]?.let { JSONObject(it.toString()) } ?: JSONObject()
                val role = m.senderType
                msg.put("id", mid).put("role", role).put("content", m.body).put("createdAt", m.timestamp)
                if (oldMessages[mid]?.optString("content") != m.body) msg.remove("originalContent")
                if (role == "assistant") {
                    msg.put("characterId", uuid("characters", m.senderId)).put("characterName", m.senderName)
                    if (oldMessages[mid]?.optString("content") != m.body) {
                        msg.remove("memoryTrace"); msg.remove("versions"); msg.remove("finishReason")
                    }
                }
                msg
            }
            val changedTranscript = LanSyncCrypto.hash(body.optJSONArray("messages") ?: JSONArray()) != LanSyncCrypto.hash(array(messages))
            if (changedTranscript) body.remove("pendingTurn") // Phone continuation supersedes the desktop's paused plan.
            val first = members.first()
            body.put("schemaVersion", 1).put("kind", "group").put("id", id)
                .put("characterId", uuid("characters", first)).put("characterName", roles.getValue(first).name)
                .put("participants", array(members.map { JSONObject().put("characterId", uuid("characters", it)).put("name", roles.getValue(it).name) }))
                .put("title", room.name).put("createdAt", room.createdAt).put("updatedAt", room.updatedAt)
                .put("messages", array(messages)).put("pinned", room.pinned).put("archived", room.isArchived)
            body.remove("deletedAt")
            result[key] = body
        }
        return result
    }

    suspend fun apply(id: String, body: JSONObject?) = db.withTransaction {
        val localId = native("groupChats", id)
        val previous = db.groupChatDao().getById(localId)
        if (body == null) {
            // Retain local-only private material; the sync ledger holds the deletion tombstone.
            if (previous != null) db.groupChatDao().upsert(previous.copy(isArchived = true))
            return@withTransaction
        }
        require(body.optString("kind") == "group") { "聊天室类型不一致" }
        val participants = objects(body.optJSONArray("participants"))
        require(participants.size in 2..8 && participants.map { it.getString("characterId") }.distinct().size == participants.size)
        val memberIds = participants.map { native("characters", it.getString("characterId")) }
        for ((index, p) in participants.withIndex()) {
            val rid = memberIds[index]
            if (db.assistantDao().getById(rid) == null) db.assistantDao().upsert(AssistantEntity(id = rid, name = p.getString("name"), enabled = false))
        }
        val existing = db.groupChatMessageDao().lanSyncByChat(localId).associateBy { it.id }
        val incoming = objects(body.optJSONArray("messages"))
        require(incoming.map { it.getString("id") }.distinct().size == incoming.size)
        for (m in incoming) {
            val mid = native("groupMessage", m.getString("id")); val old = existing[mid]
            require(db.groupChatMessageDao().getById(mid)?.chatId?.let { it == localId } != false) { "消息身份已属于其他聊天室" }
            require(old == null || old.whisperTargetId == null) { "不能覆盖手机私信" }
            val role = m.getString("role"); require(role in listOf("user", "assistant", "system"))
            val senderId = when (role) { "assistant" -> native("characters", m.getString("characterId")); "system" -> "system"; else -> "local_user" }
            require(role != "assistant" || senderId in memberIds)
            val name = when (role) { "assistant" -> m.getString("characterName"); "system" -> "系统"; else -> old?.senderName ?: "我" }
            val next = (old ?: GroupChatMessageEntity(id = mid, chatId = localId, senderType = role, senderId = senderId, senderName = name, body = "")).copy(
                senderType = role, senderId = senderId, senderName = name, body = m.getString("content"), timestamp = m.getLong("createdAt"),
                messageType = if (role == "system") "system" else old?.messageType ?: "normal",
            )
            db.groupChatMessageDao().upsert(next)
        }
        val known = objects(base("chats:$id").optJSONArray("messages")).map { native("groupMessage", it.getString("id")) }.toSet()
        val keep = incoming.map { native("groupMessage", it.getString("id")) }.toSet()
        for (removed in known - keep) if (existing[removed]?.whisperTargetId == null) db.groupChatMessageDao().deleteById(removed)
        val latest = db.groupChatMessageDao().getLatest(localId)
        val room = (previous ?: GroupChatEntity(id = localId, name = body.getString("title"), memberIdsJson = "[]", createdAt = body.getLong("createdAt"))).copy(
            name = body.getString("title"), memberIdsJson = JSONArray(memberIds).toString(), updatedAt = body.getLong("updatedAt"),
            pinned = body.optBoolean("pinned"), isArchived = body.optBoolean("archived") || body.has("deletedAt"),
            messageCount = db.groupChatMessageDao().countByChat(localId), lastMessagePreview = latest?.let { "${it.senderName}: ${it.body}" }?.take(180) ?: "",
            lastActivityAt = latest?.timestamp ?: body.getLong("updatedAt"),
        )
        db.groupChatDao().upsert(room)
    }

    /** An identity merge cannot collapse two distinct speakers in the same room. */
    suspend fun validateMerge(from: String, to: String) {
        require(db.groupChatDao().getAll().none { val members = strings(it.memberIdsJson); from in members && to in members }) {
            "两个角色在同一聊天室中，请分别保留身份"
        }
    }
    suspend fun mergeRoles(from: String, to: String) {
        for (room in db.groupChatDao().getAll()) {
            val members = strings(room.memberIdsJson)
            if (from !in members) continue
            val privateContexts = JSONObject(room.memberPrivateContextJson)
            if (privateContexts.has(from)) { privateContexts.put(to, privateContexts.get(from)); privateContexts.remove(from) }
            db.groupChatDao().upsert(room.copy(memberIdsJson = JSONArray(members.map { if (it == from) to else it }).toString(),
                hostId = if (room.hostId == from) to else room.hostId,
                observerIdsJson = JSONArray(strings(room.observerIdsJson).map { if (it == from) to else it }).toString(), memberPrivateContextJson = privateContexts.toString()))
            for (m in db.groupChatMessageDao().lanSyncByChat(room.id)) if (m.senderId == from || m.whisperTargetId == from) {
                db.groupChatMessageDao().upsert(m.copy(senderId = if (m.senderId == from) to else m.senderId, whisperTargetId = if (m.whisperTargetId == from) to else m.whisperTargetId))
            }
            for (memory in db.groupChatMemoryDao().getByGroupChat(room.id)) if (memory.assistantId == from) db.groupChatMemoryDao().insert(memory.copy(assistantId = to))
        }
    }
}
