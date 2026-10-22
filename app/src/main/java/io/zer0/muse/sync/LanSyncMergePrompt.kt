// SPDX-License-Identifier: GPL-3.0-or-later
package io.zer0.muse.sync

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@Composable
fun LanSyncMergePrompt() {
    val context = LocalContext.current
    val manager = remember { runCatching { LanSyncManager.get(context) }.getOrNull() } ?: return
    val matches by manager.nameMatches.collectAsState()
    var dismissed by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val match = matches.firstOrNull { it.optString("id") != dismissed } ?: return
    val id = match.getString("id")
    AlertDialog(
        onDismissRequest = { if (!working) dismissed = id },
        title = { Text("发现同名 AI：${match.getString("name")}") },
        text = { Text("${match.optString("deviceName", "另一台设备")}与电脑有不同身份的同名角色，要合并为同一个 AI 吗？\n\n合并后汇总聊天和记忆，设定以电脑为主；不同设定保留副本，原手机角色保留停用备份。密钥和工具授权仍留在各自设备。\n\n选择分别保留后，不会反复询问这一对角色。" + if (error.isNotBlank()) "\n\n$error" else "") },
        confirmButton = { TextButton(enabled = !working, onClick = { working = true; scope.launch { runCatching { manager.answerNameMatch(id, true) }.onSuccess { dismissed = id }.onFailure { error = "选择未能保存，请稍后重试。" }; working = false } }) { Text("合并") } },
        dismissButton = { TextButton(enabled = !working, onClick = { working = true; scope.launch { runCatching { manager.answerNameMatch(id, false) }.onSuccess { dismissed = id }.onFailure { error = "选择未能保存，请稍后重试。" }; working = false } }) { Text("分别保留") } },
    )
}
