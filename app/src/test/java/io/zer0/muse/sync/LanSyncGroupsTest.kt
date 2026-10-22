package io.zer0.muse.sync

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.zer0.muse.data.assistant.AssistantEntity
import io.zer0.muse.data.groupchat.*
import io.zer0.muse.data.session.MuseDb
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class LanSyncGroupsTest {
    private lateinit var db: MuseDb
    private lateinit var sync: LanSyncGroups
    private val bases = mutableMapOf<String, JSONObject>()
    private val roles = listOf(AssistantEntity(id = "a", name = "同名", systemPrompt = "A 人格"), AssistantEntity(id = "b", name = "同名", systemPrompt = "B 人格"), AssistantEntity(id = "c", name = "C"))
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MuseDb::class.java).allowMainThreadQueries().build()
        roles.forEach { db.assistantDao().upsert(it) }
        sync = LanSyncGroups(db, { _, id -> id }, { _, id -> id }, { bases[it]?.let { b -> JSONObject(b.toString()) } ?: JSONObject() })
        db.groupChatDao().upsert(GroupChatEntity(id = "room", name = "聊天室", memberIdsJson = "[\"a\",\"b\"]", createdAt = 1, updatedAt = 3, memberPrivateContextJson = "{\"a\":\"A 私密背景\",\"b\":\"B 私密背景\"}"))
    }
    @After fun cleanup() { db.close() }
    private fun message(id: String, sender: String = "a", text: String = id, time: Long = 2, whisper: String? = null) = GroupChatMessageEntity(id = id, chatId = "room", senderType = if (sender == "local_user") "user" else "assistant", senderId = sender, senderName = if (sender == "local_user") "我" else "同名", body = text, timestamp = time, whisperTargetId = whisper)
    @Test fun publicSpeakersRoundTripWithoutWhispersOrPrivateContext() = runBlocking {
        db.groupChatMessageDao().insertAll(listOf(message("u", "local_user", time = 1), message("a-msg"), message("b-msg", "b", time = 3), message("secret", text = "绝不能公开", whisper = "local_user")))
        val body = sync.export(roles).getValue("chats:room")
        assertEquals("group", body.getString("kind")); assertEquals(3, body.getJSONArray("messages").length())
        assertFalse(body.toString().contains("绝不能公开")); assertFalse(body.toString().contains("私密背景")); assertFalse(body.toString().contains("人格"))
        assertEquals("a", body.getJSONArray("messages").getJSONObject(1).getString("characterId"))
        assertEquals("b", body.getJSONArray("messages").getJSONObject(2).getString("characterId"))
        bases["chats:room"] = JSONObject(body.toString())
        body.getJSONArray("messages").getJSONObject(1).put("content", "电脑编辑")
        sync.apply("room", body); sync.apply("room", body)
        assertEquals(4, db.groupChatMessageDao().countByChat("room")); assertEquals("绝不能公开", db.groupChatMessageDao().getById("secret")!!.body)
        assertEquals("电脑编辑", db.groupChatMessageDao().getById("a-msg")!!.body)
        assertEquals("A 人格", db.assistantDao().getById("a")!!.systemPrompt)
        assertTrue(db.groupChatDao().getById("room")!!.memberPrivateContextJson.contains("A 私密背景"))
        assertTrue(db.sessionDao().lanSyncAll().isEmpty())
    }
    @Test fun desktopImportKeepsPlanAndTraceUntilPhoneContinues() = runBlocking {
        db.groupChatMessageDao().insertAll(listOf(message("u", "local_user", time = 1), message("a-msg")))
        val body = sync.export(roles).getValue("chats:room")
        body.put("pendingTurn", JSONObject().put("userMessageId", "u").put("characterIds", JSONArray("[\"a\",\"b\"]")).put("completedIds", JSONArray("[\"a\"]")))
        body.getJSONArray("messages").getJSONObject(1).put("memoryTrace", JSONObject().put("kept", true))
        bases["chats:room"] = body
        sync.apply("room", body)
        val untouched = sync.export(roles).getValue("chats:room")
        assertTrue(untouched.has("pendingTurn")); assertTrue(untouched.getJSONArray("messages").getJSONObject(1).has("memoryTrace"))
        db.groupChatMessageDao().upsert(message("new-user", "local_user", time = 4))
        assertFalse(sync.export(roles).getValue("chats:room").has("pendingTurn"))
    }
    @Test fun deletesOnlyPreviouslySharedMessagesAndArchivesWithoutErasingPrivateData() = runBlocking {
        db.groupChatMessageDao().insertAll(listOf(message("old"), message("secret", whisper = "local_user")))
        val body = sync.export(roles).getValue("chats:room"); bases["chats:room"] = JSONObject(body.toString())
        db.groupChatMessageDao().upsert(message("unsent", time = 4))
        body.put("messages", JSONArray()); sync.apply("room", body)
        assertNull(db.groupChatMessageDao().getById("old")); assertNotNull(db.groupChatMessageDao().getById("unsent")); assertNotNull(db.groupChatMessageDao().getById("secret"))
        sync.apply("room", null)
        assertTrue(db.groupChatDao().getById("room")!!.isArchived); assertEquals(2, db.groupChatMessageDao().countByChat("room"))
    }
    @Test fun allMessagesBeyondDisplayPageAreSyncedAndMalformedSpeakersRollBack() = runBlocking {
        db.groupChatMessageDao().insertAll((1..230).map { message("m$it", time = it.toLong()) })
        val body = sync.export(roles).getValue("chats:room"); assertEquals(230, body.getJSONArray("messages").length())
        body.getJSONArray("messages").getJSONObject(0).put("content", "不应部分写入")
        body.getJSONArray("messages").getJSONObject(229).put("characterId", "outsider")
        assertTrue(runCatching { sync.apply("room", body) }.isFailure)
        assertEquals("m1", db.groupChatMessageDao().getById("m1")!!.body)
    }
    @Test fun mergePreservesPerRoleMemoryAndRejectsCollapsedSpeakers() = runBlocking {
        db.groupChatMessageDao().upsert(message("a-msg"))
        db.groupChatMemoryDao().insert(GroupChatMemoryEntity(id = "mem-a", groupChatId = "room", assistantId = "a", summary = "A 记忆"))
        db.groupChatMemoryDao().insert(GroupChatMemoryEntity(id = "mem-b", groupChatId = "room", assistantId = "b", summary = "B 记忆"))
        assertTrue(runCatching { sync.validateMerge("a", "b") }.isFailure)
        sync.validateMerge("a", "c"); sync.mergeRoles("a", "c")
        assertEquals("c", db.groupChatMessageDao().getById("a-msg")!!.senderId)
        assertEquals("A 记忆", db.groupChatMemoryDao().getByAssistantAndChat("c", "room").single().summary)
        assertEquals("B 记忆", db.groupChatMemoryDao().getByAssistantAndChat("b", "room").single().summary)
        val contexts = JSONObject(db.groupChatDao().getById("room")!!.memberPrivateContextJson)
        assertFalse(contexts.has("a")); assertEquals("A 私密背景", contexts.getString("c")); assertEquals("B 私密背景", contexts.getString("b"))
    }
    @Test fun incompatibleLegacyRoomDoesNotDeletePreviouslySyncedCopy() = runBlocking {
        bases["chats:room"] = sync.export(roles).getValue("chats:room")
        db.groupChatMessageDao().upsert(message("team", "team-coordinator"))
        val result = sync.export(roles)
        assertEquals(1, sync.skippedRooms); assertEquals(LanSyncCrypto.hash(bases["chats:room"]), LanSyncCrypto.hash(result["chats:room"]))
    }
    @Test fun acknowledgedDeletionSurvivesAdapterRestartAndCanBeExplicitlyRestored() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val provider = io.zer0.memory.fact.FactDbProvider(context)
        try {
            db.groupChatMessageDao().upsert(message("secret", whisper = "local_user"))
            val state = JSONObject().put("deviceId", java.util.UUID.randomUUID().toString()).put("rows", JSONObject())
            val adapter = LanSyncData(context, state, db, provider)
            val entry = adapter.exportRaw().entries.single { it.value.optString("kind") == "group" }
            val id = entry.value.getString("id")
            state.getJSONObject("rows").put(entry.key, JSONObject().put("body", entry.value))
            adapter.rememberNative()
            adapter.apply("chats", id, null)
            state.getJSONObject("rows").put(entry.key, JSONObject().put("body", JSONObject.NULL).put("groupChat", true))
            adapter.rememberNative(setOf(entry.key))
            val restarted = LanSyncData(context, JSONObject(state.toString()), db, provider)
            assertEquals(JSONObject.NULL, restarted.export()[entry.key])
            assertEquals(1, db.groupChatMessageDao().countByChat("room"))
            assertTrue(db.groupChatDao().getById("room")!!.isArchived)
            db.groupChatDao().upsert(db.groupChatDao().getById("room")!!.copy(isArchived = false))
            assertEquals("group", (restarted.export()[entry.key] as JSONObject).getString("kind"))
        } finally { provider.releaseAll() }
    }
}
