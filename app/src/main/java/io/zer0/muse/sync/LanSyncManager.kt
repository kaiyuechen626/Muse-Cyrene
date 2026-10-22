// SPDX-License-Identifier: GPL-3.0-or-later
package io.zer0.muse.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import androidx.work.*
import io.zer0.muse.schedule.ChatGenerationManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.koin.core.context.GlobalContext
import java.io.*
import java.net.*
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@androidx.annotation.Keep
class LanSyncManager private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val file = AtomicFile(File(context.filesDir, "lan-sync-state.enc"))
    val status = MutableStateFlow("尚未配对电脑。请选择扫码或 USB 配对。")
    private val master: SecretKey by lazy {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("muse-lan-sync-v1", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder("muse-lan-sync-v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); generateKey()
        }
    }
    private var state: JSONObject = load()
    val nameMatches = MutableStateFlow(readMatches())
    val roleRedirects = MutableStateFlow(readRedirects())
    private fun readRedirects(): Map<String, String> = state.optJSONObject("mergedNativeRoles")?.let { o -> o.keys().asSequence().associateWith { o.getString(it) } } ?: emptyMap()
    private fun readMatches(): List<JSONObject> = state.optJSONArray("nameMatches")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } } ?: emptyList()
    suspend fun answerNameMatch(id: String, merge: Boolean) {
        mutex.withLock {
            val answers = state.optJSONObject("nameAnswers") ?: JSONObject().also { state.put("nameAnswers", it) }
            answers.put(id, merge); save(); status.value = "已保存选择，正在与电脑确认。"
        }
        syncNow()
    }
    private suspend fun finishPendingRoleMerges() {
        val pending = state.optJSONObject("pendingRoleMerges") ?: return
        val aliases = pending.getJSONArray("roleMerges"); val adapter = LanSyncData(context, state)
        for (i in 0 until aliases.length()) { val m = aliases.getJSONObject(i); adapter.mergeRoles(m.getString("from"), m.getString("to")) }
        val map = state.optJSONObject("roleAliases") ?: JSONObject().also { state.put("roleAliases", it) }
        for (i in 0 until aliases.length()) { val m = aliases.getJSONObject(i); map.put(m.getString("from"), m.getString("to")) }
        roleRedirects.value = readRedirects()
        val settings = GlobalContext.get().get<io.zer0.muse.data.SettingsRepository>()
        val defaultId = settings.getDefaultAssistantId(); var targetDefault = defaultId; val seen = mutableSetOf<String>(); while (roleRedirects.value.containsKey(targetDefault) && seen.add(targetDefault)) targetDefault = roleRedirects.value.getValue(targetDefault); if (targetDefault != defaultId) settings.saveDefaultAssistantId(targetDefault)
        state.put("roleMergeVersion", pending.getLong("roleMergeVersion")).put("identityMergeVersion", pending.getLong("roleMergeVersion"))
        state.remove("pendingRoleMerges"); save()
    }
    private var started = false
    private val cm get() = context.getSystemService(ConnectivityManager::class.java)
    private fun wifi(): Network? = cm.allNetworks.firstOrNull { n -> cm.getNetworkCapabilities(n)?.let { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } == true }
    private fun busy(): Boolean = runCatching { GlobalContext.get().get<ChatGenerationManager>().activeGenerations.value.isNotEmpty() }.getOrDefault(false)
    private fun load(): JSONObject {
        if (!file.baseFile.exists()) return JSONObject().put("deviceId", UUID.randomUUID().toString()).put("cursor", 0).put("rows", JSONObject())
        val bytes = file.openRead().use { it.readBytes() }; require(bytes.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, master, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return JSONObject(cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8))
    }
    private fun save() {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, master)
        val out = file.startWrite()
        try { out.write(cipher.iv + cipher.doFinal(state.toString().toByteArray())); file.finishWrite(out) } catch (t: Throwable) { file.failWrite(out); throw t }
    }
    fun start() {
        if (started) return; started = true
        scope.launch { while (isActive) { syncNow(); delay(20000) } }
        cm.registerNetworkCallback(android.net.NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), object : ConnectivityManager.NetworkCallback() { override fun onAvailable(network: Network) { scope.launch { syncNow() } } })
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("muse-lan-sync", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<LanSyncWorker>(15, TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
    fun pairedName(): String? = state.optJSONObject("peer")?.optString("serverName")
    fun conflictCopies(): String = (state.optJSONArray("conflicts") ?: JSONArray()).toString(2)
    suspend fun forget() = mutex.withLock { state.remove("peer"); for (key in listOf("roleMergeVersion", "identityMergeVersion", "nameAnswers", "pendingRoleMerges", "roleAliases")) state.remove(key); state.put("nameMatches", JSONArray()); nameMatches.value = emptyList(); state.put("deviceId", UUID.randomUUID().toString()).put("cursor", 0).put("rows", JSONObject()).put("nativeHashes", JSONObject()); save(); status.value = "已解除本机配对。电脑端可在同步设置中撤销该手机。" }
    private fun local(host: String): Boolean = host.matches(Regex("^(10\\.|192\\.168\\.|169\\.254\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.).*")) && host.matches(Regex("[0-9.]+"))
    /** HTTP contains only authenticated AES-GCM envelopes, never plaintext user data. */
    private fun post(host: String, port: Int, path: String, body: JSONObject, network: Network?): JSONObject {
        require((local(host) || host == "127.0.0.1") && port in 1..65535)
        val bytes = body.toString().toByteArray(); require(bytes.size <= 24_000_000)
        val socket = Socket(); network?.bindSocket(socket)
        socket.use { s ->
            s.soTimeout = 20000; s.connect(InetSocketAddress(host, port), 6000)
            val header = "POST $path HTTP/1.1\r\nHost: $host:$port\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
            s.getOutputStream().apply { write(header.toByteArray()); write(bytes); flush() }
            val input = BufferedInputStream(s.getInputStream()); fun line(): String { val buf = ByteArrayOutputStream(); while (true) { val b = input.read(); require(b >= 0); if (b == 10) break; require(buf.size() < 8192); if (b != 13) buf.write(b) }; return buf.toString("UTF-8") }
            val code = line().split(' ').getOrNull(1)?.toIntOrNull() ?: error("电脑响应无效")
            var length = -1
            while (true) { val h = line(); if (h.isEmpty()) break; if (h.startsWith("Content-Length:", true)) length = h.substringAfter(':').trim().toInt() }
            require(length in 0..24_000_000); val data = ByteArray(length); DataInputStream(input).readFully(data)
            if (code != 200) error(if (code == 403) "配对已失效，请重新配对电脑。" else "电脑暂未接受同步（$code）")
            return JSONObject(data.toString(Charsets.UTF_8))
        }
    }
    suspend fun pair(uri: String) = withContext(Dispatchers.IO) { mutex.withLock {
        require(uri.startsWith("muse-sync://pair/") && uri.length < 3000) { "不是 Muse 局域网配对码" }
        require(!busy()) { "请先等待手机聊天生成结束再配对" }
        val p = JSONObject(LanSyncCrypto.decode(uri.removePrefix("muse-sync://pair/")).toString(Charsets.UTF_8))
        require(p.getInt("v") == 1 && LanSyncCrypto.decode(p.getString("key")).size == 32)
        val host = p.getString("host"); val usb = p.optString("mode") == "usb"
        require(if (usb) host == "127.0.0.1" else local(host)) { "请使用电脑在局域网内生成的配对码" }
        val rid = UUID.randomUUID().toString(); val pairId = p.getString("pairId")
        val input = JSONObject().put("deviceId", state.getString("deviceId")).put("name", android.os.Build.MODEL.take(100))
        val request = JSONObject().put("pairId", pairId).put("requestId", rid).put("payload", LanSyncCrypto.seal(p.getString("key"), "pair:request:$pairId:$rid", input))
        val response = post(host, p.getInt("port"), "/pair", request, if (usb) null else wifi())
        require(response.getString("requestId") == rid)
        val peer = LanSyncCrypto.open(p.getString("key"), "pair:response:$pairId:$rid", response.getJSONObject("payload"))
        require(peer.getString("serverId") == p.getString("serverId") && peer.getString("deviceId") == state.getString("deviceId"))
        val addresses = peer.optJSONArray("addresses"); val lanHost = if (usb) (0 until (addresses?.length() ?: 0)).map { addresses!!.getString(it) }.firstOrNull { local(it) } ?: "" else host
        peer.put("host", lanHost); state.put("peer", peer).put("cursor", 0).put("rows", JSONObject()).put("nativeHashes", JSONObject()).put("roleMergeVersion", 0); state.remove("roleAliases"); state.remove("identityMergeVersion"); state.remove("pendingRoleMerges"); state.remove("nameAnswers"); state.put("nameMatches", JSONArray()); nameMatches.value = emptyList(); save()
        status.value = "已配对 ${peer.getString("serverName")}。在同一 Wi-Fi 内将自动同步。"
    }; syncNow() }
    private fun discover(peer: JSONObject, network: Network): String {
        val nonce = UUID.randomUUID().toString(); val query = JSONObject().put("type", "muse-sync-discover").put("deviceId", state.getString("deviceId")).put("nonce", nonce).toString().toByteArray()
        DatagramSocket().use { socket ->
            network.bindSocket(socket); socket.broadcast = true; socket.soTimeout = 2500
            val targets = linkedSetOf("255.255.255.255")
            NetworkInterface.getNetworkInterfaces().toList().flatMap { it.interfaceAddresses }.mapNotNull { it.broadcast?.hostAddress }.forEach { targets.add(it) }
            targets.forEach { runCatching { socket.send(DatagramPacket(query, query.size, InetAddress.getByName(it), peer.optInt("discoveryPort", 32113))) } }
            val end = System.currentTimeMillis() + 3000
            while (System.currentTimeMillis() < end) {
                val packet = DatagramPacket(ByteArray(2000), 2000); socket.receive(packet)
                val reply = JSONObject(String(packet.data, 0, packet.length)); val ip = packet.address.hostAddress ?: continue
                if (local(ip) && reply.optString("serverId") == peer.getString("serverId") && reply.optString("nonce") == nonce && reply.optString("mac") == LanSyncCrypto.proof(peer.getString("key"), "${peer.getString("serverId")}:${reply.getInt("port")}:$nonce")) { peer.put("port", reply.getInt("port")); return ip }
            }
        }; error("没有发现已配对电脑，请检查两端 Wi-Fi 和电脑同步开关")
    }
    suspend fun syncNow(): Unit = withContext(Dispatchers.IO) {
        if (!mutex.tryLock()) return@withContext
        try {
            val peer = state.optJSONObject("peer") ?: return@withContext
            val network = wifi() ?: run { status.value = "已配对，等待连接同一局域网。"; return@withContext }
            if (busy()) { status.value = "聊天生成中，结束后自动同步。"; return@withContext }
            finishPendingRoleMerges()
            val adapter = LanSyncData(context, state); val local = adapter.export(); val before = LanSyncCrypto.hash(JSONObject(local)); val rows = state.getJSONObject("rows")
            val changes = JSONArray()
            val submitted = mutableSetOf<String>()
            var batchBytes = 0
            val allKeys = (local.keys + rows.keys().asSequence().toList()).distinct().sortedWith(compareBy<String> { if (it.startsWith("characters:")) 0 else if (it.startsWith("chats:")) 1 else 2 }.thenBy { it })
            for (key in allKeys) {
                val aliases = state.optJSONObject("roleAliases")
                if ((key.startsWith("characters:") || key.startsWith("memories:")) && aliases?.has(key.substringAfter(':')) == true) continue
                val value = local[key] ?: JSONObject.NULL; val old = rows.optJSONObject(key); if (old != null && LanSyncCrypto.hash(value) == LanSyncCrypto.hash(old.opt("body"))) continue
                if (old == null && value == JSONObject.NULL) continue
                val revision = old?.optLong("revision") ?: 0; val parts = key.split(':', limit = 2)
                val opId = UUID.nameUUIDFromBytes((key + ":" + revision + ":" + LanSyncCrypto.hash(value)).toByteArray()).toString()
                val change = JSONObject().put("kind", parts[0]).put("id", parts[1]).put("opId", opId).put("baseRevision", revision).put("body", value)
                val changeBytes = change.toString().toByteArray().size
                require(changeBytes <= 7_800_000) { "单条资料超过同步限制，请先拆分过长对话" }
                if (changes.length() > 0 && batchBytes + changeBytes > 12_000_000) break
                changes.put(change); batchBytes += changeBytes
                submitted.add(key)
                if (changes.length() >= 16) break
            }
            if (changes.length() > 0) save() // Avoid rewriting the full ledger on an idle poll.
            val rid = UUID.randomUUID().toString(); val did = state.getString("deviceId"); val key = peer.getString("key")
            val inventory = JSONArray(); for ((rk, value) in local) if (rk.startsWith("characters:") && value is JSONObject && !value.has("deletedAt")) inventory.put(JSONObject().put("id", value.getString("id")).put("name", value.getString("name")))
            val answers = state.optJSONObject("nameAnswers") ?: JSONObject(); val decisions = JSONArray(); answers.keys().forEach { id -> decisions.put(JSONObject().put("id", id).put("merge", answers.getBoolean(id))) }
            val payload = JSONObject().put("cursor", state.optLong("cursor")).put("changes", changes).put("roleInventory", inventory).put("roleMergeVersion", state.optLong("roleMergeVersion")).put("nameDecisions", decisions).put("identityMergeVersion", state.optLong("identityMergeVersion")).put("completeIdentityMerge", state.optLong("identityMergeVersion") > 0 && changes.length() == 0)
            val request = JSONObject().put("deviceId", did).put("requestId", rid).put("payload", LanSyncCrypto.seal(key, "sync:request:$did:$rid", payload))
            val oldHost = peer.optString("host"); val oldPort = peer.optInt("port")
            val response = try { post(peer.getString("host"), peer.getInt("port"), "/exchange", request, network) } catch (_: Exception) { peer.put("host", discover(peer, network)); post(peer.getString("host"), peer.getInt("port"), "/exchange", request, network) }
            require(response.getString("requestId") == rid)
            val output = LanSyncCrypto.open(key, "sync:response:$did:$rid", response.getJSONObject("payload"))
            if (output.optBoolean("busy")) { status.value = "电脑正在处理聊天，稍后自动同步。"; return@withContext }
            require(output.optString("serverId") == peer.getString("serverId"))
            output.optJSONArray("acceptedNameDecisions")?.let { a -> for (i in 0 until a.length()) answers.remove(a.getString(i)) }
            val matchesChanged = output.has("nameMatches") && output.getJSONArray("nameMatches").toString() != state.optJSONArray("nameMatches")?.toString()
            if (output.has("nameMatches")) { state.put("nameMatches", output.getJSONArray("nameMatches")); nameMatches.value = readMatches() }
            if (output.optBoolean("completedIdentityMerge")) state.remove("identityMergeVersion")
            if (output.has("roleMerges")) {
                if (busy() || before != LanSyncCrypto.hash(JSONObject(adapter.export()))) { save(); status.value = "本地数据发生变化，稍后继续合并角色。"; return@withContext }
                state.put("pendingRoleMerges", JSONObject().put("roleMerges", output.getJSONArray("roleMerges")).put("roleMergeVersion", output.getLong("roleMergeVersion"))); save(); finishPendingRoleMerges()
                status.value = "已统一同名角色身份，继续汇总聊天和记忆。"; scope.launch { delay(200); syncNow() }; return@withContext
            }
            if (nameMatches.value.isNotEmpty()) { save(); status.value = "发现同名 AI，请选择合并或分别保留。"; return@withContext }
            if (output.getJSONArray("rows").length() == 0 && output.getJSONArray("acks").length() == 0) {
                if (matchesChanged || oldHost != peer.optString("host") || oldPort != peer.optInt("port") || decisions.length() > 0 || output.optBoolean("completedIdentityMerge")) save()
                status.value = "已连接电脑，没有待同步的修改。"; return@withContext
            }
            if (busy() || before != LanSyncCrypto.hash(JSONObject(adapter.export()))) { status.value = "本地数据发生变化，下一轮自动合并。"; return@withContext }
            val incoming = mutableMapOf<String, JSONObject>()
            for (field in listOf("rows", "acks")) { val array = output.getJSONArray(field); for (i in 0 until array.length()) { val row = array.getJSONObject(i); val rk = row.getString("kind") + ":" + row.getString("id"); if ((incoming[rk]?.optLong("revision") ?: -1) <= row.getLong("revision")) incoming[rk] = row } }
            val conflictArray = state.optJSONArray("conflicts") ?: JSONArray().also { state.put("conflicts", it) }
            val applied = mutableSetOf<String>()
            for (row in incoming.values.sortedBy { when (it.getString("kind")) { "characters" -> 0; "chats" -> 1; else -> 2 } }) {
                val rk = row.getString("kind") + ":" + row.getString("id")
                if (row.getString("kind") in listOf("characters", "memories") && state.optJSONObject("roleAliases")?.has(row.getString("id")) == true) continue
                // Keep edits waiting for a later upload batch until the server acknowledges them.
                val previous = rows.optJSONObject(rk)
                val dirty = LanSyncCrypto.hash(local[rk] ?: JSONObject.NULL) != LanSyncCrypto.hash(previous?.opt("body") ?: JSONObject.NULL)
                if (dirty && rk !in submitted) continue
                if (!row.isNull("conflictId") && row.has("conflictId")) conflictArray.put(JSONObject().put("id", row.getString("conflictId")).put("local", local[rk]).put("computer", row.opt("body")))
                if (row.getString("kind") == "memories" && rk in submitted && state.optLong("identityMergeVersion") > 0 && local[rk] is JSONObject) rows.put(rk, JSONObject().put("body", local[rk])) // Account for deduplicated facts from this acknowledged merge batch.
                try { adapter.apply(row.getString("kind"), row.getString("id"), row.optJSONObject("body")); rows.put(rk, row) } catch (failure: Exception) { if (previous == null) rows.remove(rk) else rows.put(rk, previous); throw failure }
                applied.add(rk)
            }
            state.put("cursor", output.getLong("cursor")).put("lastSync", System.currentTimeMillis()); adapter.rememberNative(applied); save()
            status.value = "已自动同步 · ${java.text.DateFormat.getTimeInstance().format(java.util.Date())}" + if (conflictArray.length() > 0) " · ${conflictArray.length()} 处冲突副本已保留" else ""
            if (output.optBoolean("hasMore") || changes.length() >= 16) scope.launch { delay(200); syncNow() }
        } catch (_: Exception) { status.value = "暂未同步成功，保留本地数据并自动重试。请确认同一 Wi-Fi、电脑服务及防火墙；必要时重新配对。" }
        finally { mutex.unlock() }
    }
    companion object {
        @Volatile private var instance: LanSyncManager? = null
        fun get(context: Context): LanSyncManager = instance ?: synchronized(this) { instance ?: LanSyncManager(context.applicationContext).also { instance = it } }
    }
}
class LanSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result { LanSyncManager.get(applicationContext).syncNow(); return Result.success() }
}
