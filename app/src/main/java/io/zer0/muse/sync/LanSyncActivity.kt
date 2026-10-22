// SPDX-License-Identifier: GPL-3.0-or-later
package io.zer0.muse.sync

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LanSyncActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { LanSyncPage(intent?.dataString, onBack = { finish() }) } } }
    }
}
@Composable
private fun LanSyncPage(initial: String?, onBack: () -> Unit) {
    val context = LocalContext.current; val manager = remember { LanSyncManager.get(context).also { it.start() } }; val scope = rememberCoroutineScope()
    val status by manager.status.collectAsState(); var uri by remember { mutableStateOf(initial.orEmpty()) }; var scanning by remember { mutableStateOf(false) }; var working by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }
    val nameMatches by manager.nameMatches.collectAsState()
    LanSyncMergePrompt()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) scanning = true else error = "扫描需要相机权限。也可从相册选择配对二维码，或选择 USB 配对。" }
    val picture = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { image -> if (image != null) scope.launch {
        runCatching { val bitmap = android.graphics.BitmapFactory.decodeStream(context.contentResolver.openInputStream(image)); io.zer0.muse.ui.qrcode.QrCodeScanner.scanFromBitmap(bitmap) }.onSuccess { raw -> if (raw?.startsWith("muse-sync://pair/") == true) uri = raw else error = "未识别到 Muse 配对二维码" }.onFailure { error = "二维码图片识别失败" }
    } }
    val conflictExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { target -> if (target != null) scope.launch { context.contentResolver.openOutputStream(target)?.use { it.write(manager.conflictCopies().toByteArray()) } } }
    val host = remember(uri) { runCatching { JSONObject(LanSyncCrypto.decode(uri.removePrefix("muse-sync://pair/")).toString(Charsets.UTF_8)).let { it.getString("host") + ":" + it.getInt("port") } }.getOrNull() }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("手机与电脑局域网同步", style = MaterialTheme.typography.headlineSmall)
        Text("首次选择扫码或 USB 连接，在这里确认配对。以后连接同一 Wi-Fi 时自动同步角色、文字聊天和记忆，断线时保留各自修改。图片、视频附件和工具明细留在原设备。")
        Text(status)
        for (match in nameMatches) {
            Text("同名 AI：${match.getString("name")} · ${match.optString("deviceName")}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !working, onClick = { scope.launch { runCatching { manager.answerNameMatch(match.getString("id"), true) }.onFailure { error = "选择未能保存，请稍后重试。" } } }) { Text("合并") }
                OutlinedButton(enabled = !working, onClick = { scope.launch { runCatching { manager.answerNameMatch(match.getString("id"), false) }.onFailure { error = "选择未能保存，请稍后重试。" } } }) { Text("分别保留") }
            }
        }
        manager.pairedName()?.let { Text("已配对电脑：$it") }
        Button(enabled = !working, onClick = { if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scanning = true else permission.launch(Manifest.permission.CAMERA) }) { Text("扫描电脑配对二维码") }
        OutlinedButton(enabled = !working, onClick = { picture.launch("image/*") }) { Text("从照片识别配对二维码") }
        Text("USB 配对：用数据线连接电脑，在电脑 Muse 的局域网同步设置中点击“USB 配对”。需要开启 USB 调试，并由你在手机系统提示中允许此电脑。手机收到配对请求后，再点击下方确认。")
        OutlinedTextField(value = uri, onValueChange = { uri = it }, label = { Text("配对内容（扫码或 USB 会自动填入）") }, modifier = Modifier.fillMaxWidth(), maxLines = 3, enabled = !working)
        if (host != null) {
            Text("准备连接：$host。请确认这是自己的电脑。同名但身份不同的角色，会先询问合并或分别保留；确认前暂停交换资料。")
            Button(enabled = !working, onClick = { working = true; error = ""; scope.launch { runCatching { manager.pair(uri.trim()) }.onSuccess { uri = "" }.onFailure { error = it.message ?: "配对失败，请在电脑重新生成配对码" }; working = false } }) { Text(if (working) "正在配对…" else "确认与此电脑配对") }
        }
        if (scanning) { Text("将电脑二维码置于画面中央"); PairQrCamera { raw -> scanning = false; uri = raw } ; TextButton(onClick = { scanning = false }) { Text("取消扫描") } }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        OutlinedButton(enabled = !working, onClick = { scope.launch { manager.syncNow() } }) { Text("立即同步") }
        OutlinedButton(enabled = !working, onClick = { conflictExport.launch("Muse-同步冲突副本.json") }) { Text("导出保留的冲突副本") }
        TextButton(enabled = !working, onClick = { scope.launch { manager.forget() } }) { Text("解除本机配对") }
        Text("密钥、DSH 授权及语音模型不参与同步。配对凭据在手机用 Android Keystore 加密保存。后台同步由系统调度，省电模式或应用被强制停止时，可能等到再次打开应用才继续。", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onBack) { Text("返回") }
    }
}
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
private fun PairQrCamera(onResult: (String) -> Unit) {
    val context = LocalContext.current; val lifecycle = LocalLifecycleOwner.current; val scope = rememberCoroutineScope()
    val previewView = remember { PreviewView(context) }; val found = remember { AtomicBoolean(false) }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxWidth().height(300.dp))
    DisposableEffect(lifecycle) {
        val executor = Executors.newSingleThreadExecutor(); val providerFuture = ProcessCameraProvider.getInstance(context)
        val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        var disposed = false; var cameraProvider: ProcessCameraProvider? = null; var analysis: ImageAnalysis? = null; var preview: Preview? = null
        providerFuture.addListener({ if (!disposed) {
            val provider = providerFuture.get(); cameraProvider = provider
            preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also { a -> a.setAnalyzer(executor) { frame ->
                val image = frame.image
                if (image == null || found.get()) frame.close() else scanner.process(InputImage.fromMediaImage(image, frame.imageInfo.rotationDegrees)).addOnSuccessListener { codes ->
                    val text = codes.firstOrNull { it.rawValue?.startsWith("muse-sync://pair/") == true }?.rawValue
                    if (text != null && found.compareAndSet(false, true)) scope.launch { onResult(text) }
                }.addOnCompleteListener { frame.close() }
            } }
            provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        } }, ContextCompat.getMainExecutor(context))
        onDispose { disposed = true; analysis?.clearAnalyzer(); cameraProvider?.let { p -> preview?.let { p.unbind(it) }; analysis?.let { p.unbind(it) } }; scanner.close(); executor.shutdown() }
    }
}
