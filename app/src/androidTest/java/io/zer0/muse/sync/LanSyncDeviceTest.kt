// SPDX-License-Identifier: GPL-3.0-or-later
package io.zer0.muse.sync

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.zer0.muse.data.assistant.AssistantEntity
import io.zer0.muse.data.session.MuseDb
import io.zer0.muse.data.session.MessageEntity
import io.zer0.muse.data.session.SessionEntity
import io.zer0.memory.fact.FactDbProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LanSyncDeviceTest {
    @Test fun computerGeneratedQrDecodesOnPhone() = runBlocking {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val bitmap = assets.open("lan-pair.gif").use { android.graphics.BitmapFactory.decodeStream(it) }
        val expected = assets.open("lan-pair.txt").bufferedReader().use { it.readText() }
        // R8 staticizes the singleton entry point. Invoke its shipped shape,
        // because a separately compiled test APK cannot rewrite invokevirtual.
        val scanner = io.zer0.muse.ui.qrcode.QrCodeScanner::class.java
        val method = scanner.declaredMethods.first {
            it.parameterTypes.size == 2 && it.parameterTypes[0] == android.graphics.Bitmap::class.java && kotlin.coroutines.Continuation::class.java.isAssignableFrom(it.parameterTypes[1])
        }.apply { isAccessible = true }
        val receiver = if (java.lang.reflect.Modifier.isStatic(method.modifiers)) null else scanner.declaredFields.first { it.type == scanner }.apply { isAccessible = true }.get(null)
        val decoded = kotlin.coroutines.suspendCoroutine<String?> { continuation ->
            @Suppress("UNCHECKED_CAST")
            val bridge = QrContinuation(continuation as kotlin.coroutines.Continuation<Any?>)
            val result = method.invoke(receiver, bitmap, bridge)
            if (result !== kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED) continuation.resumeWith(Result.success(result as? String))
        }
        assertEquals(expected, decoded)
    }
    @Test fun gcmAuthenticatesDirectionAndCanonicalIdentity() {
        val key = LanSyncCrypto.encode(ByteArray(32) { it.toByte() })
        val body = JSONObject().put("text", "手机与电脑😀").put("n", 42)
        val box = LanSyncCrypto.seal(key, "test:request", body)
        assertEquals(LanSyncCrypto.canonical(body), LanSyncCrypto.canonical(LanSyncCrypto.open(key, "test:request", box)))
        assertTrue(runCatching { LanSyncCrypto.open(key, "test:response", box) }.isFailure)
        assertEquals(LanSyncCrypto.hash(JSONObject("{\"a\":1,\"b\":2}")), LanSyncCrypto.hash(JSONObject("{\"b\":2,\"a\":1}")))
    }
    @Test fun nativeRoundTripKeepsOtherMessageFieldsAndStableIds() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, MuseDb::class.java).build()
        val provider = FactDbProvider(context)
        val roleId = UUID.randomUUID().toString(); val chatId = UUID.randomUUID().toString(); val msgId = UUID.randomUUID().toString()
        try {
            db.assistantDao().upsert(AssistantEntity(id = roleId, name = "同步测试角色", systemPrompt = "测试设定", createdAt = 1, updatedAt = 1))
            db.sessionDao().insert(SessionEntity(id = chatId, assistantId = roleId, title = "同步测试对话", createdAt = 1, updatedAt = 1))
            db.messageDao().upsert(MessageEntity(id = msgId, sessionId = chatId, role = "USER", content = "原始消息", reasoning = "保留字段", createdAt = 1, seq = 1, commitSeq = 10))
            db.messageDao().upsert(MessageEntity(id = UUID.randomUUID().toString(), sessionId = chatId, role = "USER", content = "已删除的消息", createdAt = 2, seq = 2, deletedAt = 3))
            val state = JSONObject().put("deviceId", UUID.randomUUID().toString()).put("rows", JSONObject())
            val adapter = LanSyncData(context, state, db, provider)
            val exported = adapter.exportRaw(); val chat = exported.getValue("chats:$chatId")
            assertEquals(roleId, chat.getString("characterId"))
            assertEquals(1, chat.getJSONArray("messages").length())
            chat.getJSONArray("messages").getJSONObject(0).put("content", "电脑编辑")
            chat.getJSONArray("messages").put(JSONObject().put("id", UUID.randomUUID().toString()).put("role", "assistant").put("content", "电脑新增").put("createdAt", 2))
            adapter.apply("chats", chatId, chat)
            val native = db.messageDao().lanSyncBySession(chatId).filter { it.deletedAt == null }
            assertEquals(2, native.size); assertEquals("电脑编辑", native.first().content); assertEquals("保留字段", native.first().reasoning)
            state.getJSONObject("rows").put("chats:$chatId", JSONObject().put("revision", 1).put("body", chat))
            adapter.rememberNative(); assertEquals(LanSyncCrypto.hash(chat), LanSyncCrypto.hash(adapter.export()["chats:$chatId"]))
            // Acknowledging one batch must not hide edits waiting in another batch.
            val role = exported.getValue("characters:$roleId")
            state.getJSONObject("rows").put("characters:$roleId", JSONObject().put("revision", 1).put("body", role))
            db.assistantDao().upsert(db.assistantDao().getById(roleId)!!.copy(name = "未上传的修改"))
            adapter.rememberNative(setOf("chats:$chatId"))
            assertEquals("未上传的修改", (adapter.export()["characters:$roleId"] as JSONObject).getString("name"))
            val factId = UUID.randomUUID().toString()
            val memory = JSONObject().put("id", roleId).put("characterId", roleId).put("records", JSONArray().put(JSONObject().put("id", factId).put("text", "隔离测试记忆").put("createdAt", 1).put("importance", 2).put("category", "preference")))
            adapter.apply("memories", roleId, memory); adapter.apply("memories", roleId, memory)
            val savedFacts = provider.getFactDb(roleId).factDao().getAll()
            assertEquals(1, savedFacts.size); assertEquals("隔离测试记忆", savedFacts.single().fact)
            assertEquals(factId, adapter.exportRaw().getValue("memories:$roleId").getJSONArray("records").getJSONObject(0).getString("id"))
        } finally { db.close(); provider.releaseAll(); context.deleteDatabase("facts_$roleId.db") }
    }
    @Test fun confirmedIdentityMergeCoalescesNativeRolesSessionsAndFacts() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, MuseDb::class.java).build(); val provider = FactDbProvider(context)
        val a = UUID.randomUUID().toString(); val b = UUID.randomUUID().toString(); val localId = "assistant-local-${UUID.randomUUID()}"
        try {
            db.assistantDao().upsert(AssistantEntity(id = a, name = "同名 AI", providerId = "phone-source-provider", systemPrompt = "手机设定"))
            db.assistantDao().upsert(AssistantEntity(id = b, name = "同名 AI", providerId = "phone-target-provider", systemPrompt = "电脑设定"))
            val chat = UUID.randomUUID().toString(); db.sessionDao().insert(SessionEntity(id = chat, assistantId = a, title = "原手机聊天", createdAt = 1, updatedAt = 1))
            provider.getFactDb(a).factDao().insert(io.zer0.memory.fact.FactEntity(fact = "手机记忆", createdAt = "2026-01-01T00:00:00Z"))
            provider.getFactDb(b).factDao().insert(io.zer0.memory.fact.FactEntity(fact = "电脑记忆", createdAt = "2026-01-01T00:00:00Z"))
            val state = JSONObject().put("deviceId", UUID.randomUUID().toString()).put("rows", JSONObject()); val adapter = LanSyncData(context, state, db, provider)
            adapter.exportRaw(); adapter.mergeRoles(a, b); adapter.mergeRoles(a, b)
            val result = adapter.exportRaw()
            assertFalse(result.containsKey("characters:$a")); assertTrue(result.containsKey("characters:$b"))
            assertEquals(b, db.sessionDao().getById(chat)!!.assistantId)
            assertEquals(2, result.getValue("memories:$b").getJSONArray("records").length())
            assertEquals("phone-target-provider", db.assistantDao().getById(b)!!.providerId)
            assertEquals("phone-source-provider", db.assistantDao().getById(a)!!.providerId)
            assertFalse(db.assistantDao().getById(a)!!.enabled)
            assertEquals(1, provider.getFactDb(a).factDao().getAll().size) // Original private profile/facts remain as a disabled backup.
            db.assistantDao().upsert(AssistantEntity(id = localId, name = "只有手机", providerId = "local-provider"))
            val before = adapter.exportRaw().values.first { it.optString("name") == "只有手机" }.getString("id"); val canonical = UUID.randomUUID().toString()
            adapter.mergeRoles(before, canonical)
            assertTrue(adapter.exportRaw().containsKey("characters:$canonical")); assertEquals("local-provider", db.assistantDao().getById(localId)!!.providerId)
        } finally { db.close(); provider.releaseAll(); for (id in listOf(a, b, localId)) context.deleteDatabase("facts_$id.db") }
    }
    @Test fun encryptedPairingAndExchangeWithRealDesktopServer() {
        val args = InstrumentationRegistry.getArguments(); val uri = args.getString("museLanFixtureUri").orEmpty()
        assumeTrue("Requires disposable desktop fixture", uri.startsWith("muse-sync://pair/"))
        val p = JSONObject(LanSyncCrypto.decode(uri.substringAfter("muse-sync://pair/")).toString(Charsets.UTF_8))
        val manager = LanSyncManager.get(InstrumentationRegistry.getInstrumentation().targetContext)
        val method = LanSyncManager::class.java.declaredMethods.first { it.name == "post" }.apply { isAccessible = true }
        fun post(path: String, body: JSONObject): JSONObject = method.invoke(manager, args.getString("museLanFixtureHost") ?: "127.0.0.1", p.getInt("port"), path, body, null) as JSONObject
        val id = UUID.randomUUID().toString(); val rid = UUID.randomUUID().toString(); val pid = p.getString("pairId")
        val response = post("/pair", JSONObject().put("pairId", pid).put("requestId", rid).put("payload", LanSyncCrypto.seal(p.getString("key"), "pair:request:$pid:$rid", JSONObject().put("deviceId", id).put("name", "Android isolated test"))))
        val peer = LanSyncCrypto.open(p.getString("key"), "pair:response:$pid:$rid", response.getJSONObject("payload"))
        var mergeVersion = 0L; var aliases = JSONArray()
        fun exchange(value: JSONObject): JSONObject {
            repeat(5) {
                val requestId = UUID.randomUUID().toString(); value.put("roleMergeVersion", mergeVersion)
                val out = post("/exchange", JSONObject().put("deviceId", id).put("requestId", requestId).put("payload", LanSyncCrypto.seal(peer.getString("key"), "sync:request:$id:$requestId", value)))
                val result = LanSyncCrypto.open(peer.getString("key"), "sync:response:$id:$requestId", out.getJSONObject("payload"))
                if (!result.has("roleMerges")) return result
                aliases = result.getJSONArray("roleMerges"); mergeVersion = result.getLong("roleMergeVersion")
            }; error("Role mapping did not settle")
        }
        val result = exchange(JSONObject().put("cursor", 0).put("changes", JSONArray()))
        assertEquals(p.getString("serverId"), result.getString("serverId")); assertTrue(result.getJSONArray("rows").length() > 0)
        val row = (0 until result.getJSONArray("rows").length()).map { result.getJSONArray("rows").getJSONObject(it) }.first { it.getString("kind") == "characters" && !it.isNull("body") }
        val changedName = "手机回传已验证：${UUID.randomUUID()}"
        val changed = JSONObject(row.getJSONObject("body").toString()).put("name", changedName)
        val accepted = exchange(JSONObject().put("cursor", result.getLong("cursor")).put("changes", JSONArray().put(JSONObject().put("kind", "characters").put("id", row.getString("id")).put("opId", UUID.randomUUID().toString()).put("baseRevision", row.getLong("revision")).put("body", changed))))
        assertEquals(changedName, accepted.getJSONArray("acks").getJSONObject(0).getJSONObject("body").getString("name"))
        val duplicateId = UUID.randomUUID().toString(); val duplicate = JSONObject(changed.toString()).put("id", duplicateId).put("systemPrompt", "手机独立设定")
        val pending = exchange(JSONObject().put("cursor", accepted.getLong("cursor")).put("changes", JSONArray().put(JSONObject().put("kind", "characters").put("id", duplicateId).put("opId", UUID.randomUUID().toString()).put("baseRevision", 0).put("body", duplicate))))
        assertEquals(0, pending.getJSONArray("acks").length()); val question = pending.getJSONArray("nameMatches").getJSONObject(0)
        exchange(JSONObject().put("cursor", pending.getLong("cursor")).put("changes", JSONArray()).put("nameDecisions", JSONArray().put(JSONObject().put("id", question.getString("id")).put("merge", true))))
        assertTrue((0 until aliases.length()).any { aliases.getJSONObject(it).getString("from") == duplicateId })
        val target = question.getString("targetId")
        val facts = JSONArray(); repeat(2) { facts.put(JSONObject().put("id", UUID.randomUUID().toString()).put("text", "同一条同步事实").put("category", "preference").put("importance", it + 1).put("createdAt", 1)) }
        val memory = JSONObject().put("id", target).put("characterId", target).put("records", facts)
        val seeded = exchange(JSONObject().put("cursor", 0).put("identityMergeVersion", mergeVersion).put("changes", JSONArray().put(JSONObject().put("kind", "memories").put("id", target).put("opId", UUID.randomUUID().toString()).put("baseRevision", 0).put("body", memory))))
        assertEquals(1, seeded.getJSONArray("acks").getJSONObject(0).getJSONObject("body").getJSONArray("records").length())
    }
}
