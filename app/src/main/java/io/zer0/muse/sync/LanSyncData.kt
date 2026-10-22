// SPDX-License-Identifier: GPL-3.0-or-later
package io.zer0.muse.sync

import android.content.Context
import androidx.room.withTransaction
import io.zer0.muse.data.assistant.AssistantEntity
import io.zer0.muse.data.session.MessageEntity
import io.zer0.muse.data.session.MuseDatabases
import io.zer0.muse.data.session.SessionEntity
import io.zer0.memory.fact.FactDbProvider
import io.zer0.memory.fact.FactEntity
import org.json.JSONArray
import org.json.JSONObject
import org.koin.core.context.GlobalContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** Projects portable conversation data only. Provider credentials and tools stay local. */
@androidx.annotation.Keep
class LanSyncData(private val context: Context, private val state: JSONObject,
    private val db: io.zer0.muse.data.session.MuseDb = MuseDatabases.muse(context),
    private val factProvider: FactDbProvider? = null,
) {
    private val facts: FactDbProvider get() = factProvider ?: GlobalContext.get().get()
    private val mappings = state.optJSONObject("mappings") ?: JSONObject().also { state.put("mappings", it) }
    private val nativeHashes = state.optJSONObject("nativeHashes") ?: JSONObject().also { state.put("nativeHashes", it) }
    private val rows = state.optJSONObject("rows") ?: JSONObject().also { state.put("rows", it) }
    private val retired = state.optJSONObject("mergedNativeRoles") ?: JSONObject().also { state.put("mergedNativeRoles", it) }
    private fun factId(role: String, fact: FactEntity): String {
        val origin = fact.source.removePrefix("lan-sync:").takeIf { fact.source.startsWith("lan-sync:") && it.matches(Regex("[a-f0-9-]{36}|[a-f0-9]{64}")) }
        return origin?.also { mappings.put("fact:$role:${fact.id}", it) } ?: uuid("fact:$role", fact.id.toString())
    }
    /** Retain the native profile on this device, coalesce its sessions/facts, and archive the duplicate. */
    suspend fun mergeRoles(from: String, to: String) {
        if (from == to) return
        val all = db.assistantDao().getAll()
        fun find(id: String): String? = all.firstOrNull { !retired.has(it.id) && uuid("characters", it.id) == id }?.id
        val sourceId = find(from) ?: return
        val targetId = find(to)
        if (targetId == null || targetId == sourceId) {
            mappings.put("characters:$sourceId", to)
        } else {
            val sourceFacts = facts.getFactDb(sourceId).factDao().getAll()
            val targetDao = facts.getFactDb(targetId).factDao()
            val targetFacts = targetDao.getAll().associateBy { factId(targetId, it) }.toMutableMap()
            for (f in sourceFacts) {
                var fid = factId(sourceId, f)
                val same = targetFacts[fid]
                if (same != null && same.fact == f.fact && same.category == f.category) {
                    if (f.importance > same.importance) {
                        val raised = same.copy(importance = f.importance)
                        targetDao.insert(raised); targetFacts[fid] = raised
                    }
                    continue
                }
                if (same != null) fid = UUID.nameUUIDFromBytes(("identity-merge:$from:$to:$fid:${f.fact}:${f.category}").toByteArray()).toString()
                if (targetFacts.containsKey(fid)) continue
                val copy = f.copy(id = 0, source = "lan-sync:$fid")
                val savedId = targetDao.insert(copy)
                mappings.put("fact:$targetId:$savedId", fid); targetFacts[fid] = copy.copy(id = savedId)
            }
            facts.getFactStore(targetId).rebuildFtsIndex()
            db.withTransaction {
                for (session in db.sessionDao().lanSyncAll()) if (session.assistantId == sourceId) db.sessionDao().update(session.copy(assistantId = targetId))
                val source = db.assistantDao().getById(sourceId)!!; val target = db.assistantDao().getById(targetId)!!
                db.assistantDao().upsert(target.copy(enabled = target.enabled || source.enabled))
                db.assistantDao().upsert(source.copy(enabled = false, name = if (source.name.startsWith("已合并备份 · ")) source.name else "已合并备份 · ${source.name}"))
            }
            retired.put(sourceId, targetId); mappings.remove("characters:$sourceId"); mappings.put("characters:$targetId", to)
        }
        for (kind in listOf("characters", "memories")) {
            rows.remove("$kind:$from"); nativeHashes.remove("$kind:$from"); nativeHashes.remove("$kind:$to")
        }
    }
    private fun uuid(kind: String, native: String): String {
        val key = "$kind:$native"
        return mappings.optString(key).ifEmpty {
            val id = runCatching { UUID.fromString(native).toString() }.getOrElse { UUID.nameUUIDFromBytes((state.getString("deviceId") + ":" + key).toByteArray()).toString() }
            mappings.put(key, id); id
        }
    }
    private fun native(kind: String, id: String): String {
        val prefix = "$kind:"
        return mappings.keys().asSequence().firstOrNull { it.startsWith(prefix) && mappings.optString(it) == id }?.removePrefix(prefix)
            ?: id.also { mappings.put(prefix + it, id) }
    }
    private fun base(key: String): JSONObject = rows.optJSONObject(key)?.optJSONObject("body")?.let { JSONObject(it.toString()) } ?: JSONObject()
    private fun time(value: String?): Long = value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrElse { runCatching { LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrDefault(0) } } ?: 0
    private fun objArray(values: List<JSONObject>): JSONArray = JSONArray().also { a -> values.forEach { a.put(it) } }
    private fun items(array: JSONArray?): List<JSONObject> = if (array == null) emptyList() else (0 until array.length()).map { array.getJSONObject(it) }
    private fun prompt(body: JSONObject): String = listOf("systemPrompt", "persona", "scenario", "examples", "postHistory").map { body.optString(it) }.filter { it.isNotBlank() }.joinToString("\n\n")
    private fun avatar(value: String): String {
        if (value.startsWith("data:image/")) return value
        if (!value.startsWith("file:") && !value.startsWith("content:")) return ""
        return runCatching {
            val bytes = context.contentResolver.openInputStream(android.net.Uri.parse(value))?.use { stream ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192); var size = 0
                while (true) { val count = stream.read(buffer); if (count < 0) break; size += count; require(size <= 2_000_000); out.write(buffer, 0, count) }; out.toByteArray()
            } ?: return ""
            val type = if (bytes.size > 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte()) "jpeg" else if (bytes.size > 12 && String(bytes.copyOfRange(8, 12)) == "WEBP") "webp" else "png"
            "data:image/$type;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }.getOrDefault("")
    }

    suspend fun exportRaw(): Map<String, JSONObject> {
        val result = linkedMapOf<String, JSONObject>()
        val assistants = db.assistantDao().getAll().filter { !retired.has(it.id) }
        for (a in assistants) {
            val id = uuid("characters", a.id); val key = "characters:$id"; val body = base(key)
            if (prompt(body) != a.systemPrompt) {
                body.put("systemPrompt", a.systemPrompt)
                for (field in listOf("persona", "scenario", "examples", "postHistory")) body.put(field, "")
            }
            val sharedAvatar = avatar(a.avatarImageUrl).ifEmpty { if (a.avatarImageUrl.isNotEmpty()) body.optString("avatar") else "" }
            body.put("id", id).put("schemaVersion", 1).put("name", a.name).put("avatar", sharedAvatar)
                .put("createdAt", a.createdAt).put("updatedAt", a.updatedAt)
            for (field in listOf("persona", "scenario", "greeting", "examples", "postHistory", "avatar", "background")) if (!body.has(field)) body.put(field, "")
            if (!body.has("model")) body.put("model", "deepseek-chat")
            if (!body.has("temperature")) body.put("temperature", a.temperature?.toDouble() ?: .8)
            if (!body.has("maxTokens")) body.put("maxTokens", a.maxTokens?.coerceIn(1, 8192) ?: 2048)
            if (!a.enabled) body.put("deletedAt", a.updatedAt) else body.remove("deletedAt")
            result[key] = body
            val memKey = "memories:$id"; val mem = base(memKey)
            val factRows = facts.getFactDb(a.id).factDao().getAll()
            val oldMem = items(mem.optJSONArray("records")).associateBy { it.getString("id") }
            val records = factRows.map { f ->
                val fid = factId(a.id, f)
                val record = oldMem[fid]?.let { JSONObject(it.toString()) } ?: JSONObject()
                record.put("id", fid).put("text", f.fact).put("createdAt", time(f.createdAt)).put("category", f.category)
                if (!record.has("importance") || record.optInt("importance").coerceIn(0, 2) != f.importance) record.put("importance", f.importance)
                record.remove("deletedAt"); record
            }
            mem.put("id", id).put("characterId", id).put("records", objArray(records)); result[memKey] = mem
        }
        for (s in db.sessionDao().lanSyncAll()) {
            val id = uuid("chats", s.id); val key = "chats:$id"; val body = base(key)
            val role = assistants.find { it.id == s.assistantId } ?: continue
            val oldMessages = items(body.optJSONArray("messages")).associateBy { it.getString("id") }
            val messages = db.messageDao().lanSyncBySession(s.id).filter { it.deletedAt == null && it.role in listOf("USER", "ASSISTANT") }.map { m ->
                val mid = uuid("message", m.id)
                val msg = oldMessages[mid]?.let { JSONObject(it.toString()) } ?: JSONObject()
                msg.put("id", mid).put("role", m.role.lowercase()).put("content", m.content).put("createdAt", m.createdAt)
                if (m.favorite) msg.put("favoriteAt", msg.optLong("favoriteAt", m.createdAt)) else msg.remove("favoriteAt")
                msg
            }
            body.put("schemaVersion", 1).put("id", id).put("characterId", uuid("characters", s.assistantId)).put("characterName", role.name)
                .put("title", s.title).put("createdAt", s.createdAt).put("updatedAt", s.updatedAt).put("messages", objArray(messages))
                .put("pinned", s.pinned).put("archived", s.archived)
            if (s.deletedAt != null) body.put("deletedAt", s.deletedAt) else body.remove("deletedAt")
            if (s.parentSessionId != null) body.put("parentChatId", uuid("chats", s.parentSessionId))
            result[key] = body
        }
        return result
    }
    suspend fun export(): Map<String, Any> = exportRaw().mapValues { (key, raw) ->
        if (nativeHashes.optString(key) == LanSyncCrypto.hash(raw) && rows.has(key)) rows.getJSONObject(key).opt("body") ?: JSONObject.NULL else raw
    }
    suspend fun rememberNative(keys: Set<String>? = null) { exportRaw().forEach { (key, body) -> if (keys == null || key in keys) nativeHashes.put(key, LanSyncCrypto.hash(body)) } }

    suspend fun apply(kind: String, id: String, body: JSONObject?) {
        when (kind) {
            "characters" -> db.withTransaction {
                val localId = native(kind, id); val previous = db.assistantDao().getById(localId)
                if (body == null) { if (previous != null) db.assistantDao().upsert(previous.copy(enabled = false)); return@withTransaction }
                val a = previous ?: AssistantEntity(id = localId, name = body.getString("name"))
                val sharedAvatar = body.optString("avatar")
                val avatarPath = if (sharedAvatar.startsWith("data:image/") && sharedAvatar.contains(";base64,")) {
                    val directory = java.io.File(context.filesDir, "lan-sync-avatars").apply { mkdirs() }
                    val file = java.io.File(directory, "$id.image"); file.writeBytes(android.util.Base64.decode(sharedAvatar.substringAfter(";base64,"), android.util.Base64.DEFAULT)); android.net.Uri.fromFile(file).toString()
                } else if (body.has("avatar")) "" else a.avatarImageUrl
                db.assistantDao().upsert(a.copy(name = body.getString("name"), systemPrompt = prompt(body), avatarImageUrl = avatarPath, enabled = !body.has("deletedAt"), createdAt = body.optLong("createdAt", a.createdAt), updatedAt = body.optLong("updatedAt", a.updatedAt)))
            }
            "chats" -> db.withTransaction {
                val localId = native(kind, id); val previous = db.sessionDao().getById(localId)
                if (body == null) { if (previous != null) db.sessionDao().update(previous.copy(deletedAt = System.currentTimeMillis())); return@withTransaction }
                val roleId = native("characters", body.getString("characterId"))
                if (db.assistantDao().getById(roleId) == null) db.assistantDao().upsert(AssistantEntity(id = roleId, name = body.optString("characterName", "已删除角色"), enabled = false))
                val existing = db.messageDao().lanSyncBySession(localId).associateBy { it.id }
                val incoming = items(body.optJSONArray("messages"))
                val session = (previous ?: SessionEntity(id = localId, title = body.getString("title"), createdAt = body.getLong("createdAt"), updatedAt = body.getLong("updatedAt"))).copy(
                    title = body.getString("title"), assistantId = roleId, updatedAt = body.getLong("updatedAt"), archived = body.optBoolean("archived"), pinned = body.optBoolean("pinned"),
                    deletedAt = if (body.has("deletedAt")) body.getLong("deletedAt") else null, messageCount = incoming.size, lastMessagePreview = incoming.lastOrNull()?.optString("content")?.take(180) ?: "",
                )
                if (previous == null) db.sessionDao().insert(session) else db.sessionDao().update(session)
                var seq = existing.values.maxOfOrNull { maxOf(it.seq, it.commitSeq) } ?: 0
                for (m in incoming) {
                    val mid = native("message", m.getString("id")); val old = existing[mid]
                    val next = (old ?: MessageEntity(id = mid, sessionId = localId, role = m.getString("role").uppercase(), content = m.getString("content"), createdAt = m.getLong("createdAt"), seq = ++seq, commitSeq = seq)).copy(
                        content = m.getString("content"), role = m.getString("role").uppercase(), favorite = m.has("favoriteAt"), deletedAt = null,
                    )
                    db.messageDao().upsert(next)
                }
                val knownBefore = items(base("chats:$id").optJSONArray("messages")).map { native("message", it.getString("id")) }.toSet()
                val now = incoming.map { native("message", it.getString("id")) }.toSet()
                for (removed in knownBefore - now) db.messageDao().deleteById(removed)
            }
            "memories" -> {
                val roleId = native("characters", id); val factDb = facts.getFactDb(roleId); val dao = factDb.factDao(); val existing = dao.getAll().associateBy { it.id }
                val incoming = items(body?.optJSONArray("records")); val keep = mutableSetOf<Long>()
                factDb.withTransaction {
                    for (r in incoming) {
                        val mapKey = "fact:$roleId"; val rid = r.getString("id"); val oldId = native(mapKey, rid).toLongOrNull(); val old = existing[oldId] ?: existing.values.find { it.source == "lan-sync:$rid" }
                        if (r.has("deletedAt")) { if (old != null) dao.deleteById(old.id); continue }
                        val fact = (old ?: FactEntity(fact = r.getString("text"), source = "lan-sync:$rid", createdAt = Instant.ofEpochMilli(r.optLong("createdAt", System.currentTimeMillis())).toString())).copy(
                            fact = r.getString("text"), importance = r.optInt("importance").coerceIn(0, 2), category = r.optString("category"),
                        )
                        val savedId = dao.insert(fact); mappings.remove("$mapKey:$rid"); mappings.put("$mapKey:$savedId", rid); keep.add(savedId)
                    }
                    val known = items(base("memories:$id").optJSONArray("records")).mapNotNull { native("fact:$roleId", it.getString("id")).toLongOrNull() }.toSet()
                    for (removed in known - keep) if (existing.containsKey(removed)) dao.deleteById(removed)
                }
                facts.getFactStore(roleId).rebuildFtsIndex()
            }
        }
    }
}
