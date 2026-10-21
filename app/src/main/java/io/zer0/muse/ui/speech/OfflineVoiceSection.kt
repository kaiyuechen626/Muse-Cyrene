// Muse Cyrene modification notice: 2026-10-18; GPL-3.0-or-later.
package io.zer0.muse.ui.speech

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.cyrene.voice.core.VoicePackStore
import io.zer0.muse.data.MediaConfig
import io.zer0.muse.data.SettingsRepository
import io.zer0.muse.R
import io.zer0.muse.ui.common.settings.SettingsSwitchRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun OfflineVoiceSection(config: MediaConfig, settings: SettingsRepository, ttsManager: TtsManager) {
    val context = LocalContext.current
    val store = remember { VoicePackStore(File(context.filesDir, "cyrene-voice-packs")) }
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("从 Cyrene Voice 导入完整音色包，离线朗读。") }
    var choices by remember { mutableStateOf(emptyList<Triple<String, String, String>>()) }
    LaunchedEffect(revision) {
        choices = withContext(Dispatchers.IO) {
            store.packs().flatMap { p -> store.roles(p).map { Triple(p.name, it.id, it.name) } }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) scope.launch {
            busy = true
            message = "正在导入并校验音色包…"
            try {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { store.importPack(it) } }
                revision++
                message = "导入完成，请选择下面的角色。"
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) { message = "导入失败：${e.message}" }
            finally { busy = false }
        }
    }
    Column {
        Text("Cyrene 离线角色音色")
        Text(message)
        TextButton(enabled = !busy, onClick = { picker.launch(arrayOf("*/*")) }) { Text("导入 .cyvoice 音色包") }
        choices.forEach { (pack, role, name) ->
            val key = "$pack/$role"
            TextButton(enabled = !busy, onClick = {
                scope.launch {
                    ttsManager.stop()
                    settings.saveMediaConfig(config.copy(ttsEngine = "cyrene-offline", ttsVoice = key))
                    message = "已启用：$name（完全离线）"
                }
            }) { Text("${if (config.ttsEngine == "cyrene-offline" && config.ttsVoice == key) "● " else "○ "}$name") }
        }
        if (config.ttsEngine == "cyrene-offline") {
            SettingsSwitchRow(
                title = stringResource(R.string.local_tts_fast_start),
                subtitle = stringResource(R.string.local_tts_fast_start_hint),
                checked = config.cyreneFastSynthesis,
                onCheckedChange = { enabled ->
                    if (!busy) scope.launch {
                        ttsManager.stop()
                        settings.saveMediaConfig(config.copy(cyreneFastSynthesis = enabled))
                    }
                },
            )
            TextButton(onClick = { ttsManager.speak("你好，这段声音由手机离线生成。", "offline-voice-preview") }) { Text("试听当前角色") }
        }
    }
}
