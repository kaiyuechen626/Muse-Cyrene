package io.zer0.muse.ui.speech

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.zer0.muse.data.MediaConfig
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

/** Run only in the isolated .ttstest build, with STREAM_MUSIC muted by the test operator. */
@RunWith(AndroidJUnit4::class)
class LocalReadingDeviceTest {
    @Test
    fun backgroundServiceControlsLocalPlayback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertTrue(context.packageName.endsWith(".ttstest"))
        assertEquals(0, context.getSystemService(android.media.AudioManager::class.java)
            .getStreamVolume(android.media.AudioManager.STREAM_MUSIC))
        val manager = org.koin.core.context.GlobalContext.get().get<TtsManager>()
        fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
        fun await(label: String, condition: () -> Boolean) {
            val end = android.os.SystemClock.elapsedRealtime() + 45_000
            while (!condition() && android.os.SystemClock.elapsedRealtime() < end) Thread.sleep(50)
            assertTrue("$label: ${manager.playbackState.value}; ${manager.engineError.value}", condition())
        }
        fun service(action: String? = null) {
            val intent = android.content.Intent(context, ReadingPlaybackService::class.java).setAction(action)
            main { androidx.core.content.ContextCompat.startForegroundService(context, intent) }
        }
        val config = MediaConfig(ttsSystemEngine = "io.cyrene.voice", ttsLanguage = "zh-CN", keepReadingOnLeave = true)
        kotlinx.coroutines.runBlocking {
            org.koin.core.context.GlobalContext.get().get<io.zer0.muse.data.SettingsRepository>().saveMediaConfig(config)
        }
        val activity = instrumentation.startActivitySync(context.packageManager
            .getLaunchIntentForPackage(context.packageName)!!.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            main { manager.applyConfig(config) }
            await("initialization") { manager.isReady.value }
            main { assertTrue(manager.speak("这是后台本地朗读测试，暂停以后继续播放，再停止朗读。", "background")) }
            service()
            await("foreground service notification") {
                context.getSystemService(android.app.NotificationManager::class.java).activeNotifications.any { it.id == 7412 }
            }
            main { assertTrue("Test activity must move to background", activity.moveTaskToBack(true)) }
            await("background playback advances") { manager.playbackState.value.positionMs > 250 }
            service("toggle_reading")
            await("service pauses") { manager.playbackState.value.status == PlaybackStatus.Paused }
            val paused = manager.playbackState.value.positionMs
            Thread.sleep(1000)
            assertTrue(kotlin.math.abs(manager.playbackState.value.positionMs - paused) < 200)
            service("toggle_reading")
            await("service resumes") { manager.playbackState.value.positionMs > paused + 200 }
            assertNull(manager.engineError.value)
            service("stop_reading")
            await("service stops") { manager.playbackState.value.status == PlaybackStatus.Idle && !manager.hasActiveReading() }
        } finally {
            main { manager.stop(); activity.finish() }
            context.stopService(android.content.Intent(context, ReadingPlaybackService::class.java))
        }
    }

    @Test
    fun directSilentEngineProbe() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertTrue(context.packageName.endsWith(".ttstest"))
        val initialized = java.util.concurrent.CountDownLatch(1)
        val finished = java.util.concurrent.CountDownLatch(1)
        var status = -1
        var error: Int? = null
        lateinit var tts: android.speech.tts.TextToSpeech
        val file = java.io.File(context.cacheDir, "direct-local-probe.wav")
        instrumentation.runOnMainSync {
            tts = android.speech.tts.TextToSpeech(context, { status = it; initialized.countDown() }, "io.cyrene.voice")
        }
        try {
            assertTrue(initialized.await(20, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(android.speech.tts.TextToSpeech.SUCCESS, status)
            tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { finished.countDown() }
                @Deprecated("Legacy callback")
                override fun onError(id: String?) { error = -1; finished.countDown() }
                override fun onError(id: String?, code: Int) { error = code; finished.countDown() }
            })
            val start = android.os.SystemClock.elapsedRealtime()
            assertEquals(android.speech.tts.TextToSpeech.SUCCESS,
                tts.synthesizeToFile("你好，今天我们一起验证手机本地语音。", android.os.Bundle(), file, "direct-probe"))
            val completed = finished.await(120, java.util.concurrent.TimeUnit.SECONDS)
            val report = "completed=$completed error=$error bytes=${file.length()} elapsedMs=${android.os.SystemClock.elapsedRealtime() - start}"
            instrumentation.sendStatus(2, android.os.Bundle().apply { putString("probe", report) })
            assertTrue(report, completed && error == null && file.length() > 44)
        } finally {
            tts.stop()
            tts.shutdown()
            file.delete()
        }
    }

    @Test
    fun localSynthesisPlaybackControlsAndReplacement() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertTrue("Must not run against the user's installed Muse", context.packageName.endsWith(".ttstest"))
        val audio = context.getSystemService(android.media.AudioManager::class.java)
        assertEquals("Device must be muted before this test", 0, audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC))
        val engine = InstrumentationRegistry.getArguments().getString("ttsEngine") ?: "io.cyrene.voice"
        val events = Collections.synchronizedList(mutableListOf<Pair<String?, Boolean>>())
        val watchdog = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
        watchdog.scheduleAtFixedRate({
            val stacks = Thread.getAllStackTraces().entries.joinToString("\n\n") { (thread, stack) ->
                "${thread.name} ${thread.state}\n" + stack.joinToString("\n")
            }
            java.io.File(context.getExternalFilesDir(null), "local-reading-threads.txt").writeText(stacks)
        }, 20, 20, java.util.concurrent.TimeUnit.SECONDS)
        fun stage(value: String) {
            instrumentation.sendStatus(2, android.os.Bundle().apply { putString("stage", value) })
        }
        lateinit var manager: TtsManager
        fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
        fun await(label: String, timeout: Long = 45_000, condition: () -> Boolean) {
            val end = android.os.SystemClock.elapsedRealtime() + timeout
            while (!condition() && android.os.SystemClock.elapsedRealtime() < end) Thread.sleep(50)
            assertTrue("$label; state=${manager.playbackState.value}; error=${manager.engineError.value}", condition())
        }
        stage("create manager")
        main {
            val neverNetwork = OkHttpClient.Builder().addInterceptor { throw AssertionError("Cloud TTS must never be used") }.build()
            manager = TtsManager(context, CloudTtsService(neverNetwork))
            manager.onStateChange = { id, active -> events.add(id to active) }
            manager.applyConfig(MediaConfig(ttsSystemEngine = engine, ttsLanguage = "zh-CN"))
        }
        try {
            stage("await initialization")
            await("engine initialization", 20_000) { manager.isReady.value }
            stage("start synthesis")
            main {
                assertTrue(manager.localSpeechOnly)
                assertTrue(manager.getAvailableEngines().any { it.name == engine })
                assertTrue(manager.getAvailableVoices().isNotEmpty())
                assertTrue(manager.getAvailableVoices().none { it.isNetworkConnectionRequired })
                assertTrue(manager.speak("你好，今天我们一起验证手机本地语音，暂停之后还可以继续播放。", "controls"))
            }
            stage("await audio position")
            await("file synthesized and MediaPlayer advancing") { manager.playbackState.value.positionMs > 250 }
            stage("pause")
            main { manager.pause() }
            val paused = manager.playbackState.value.positionMs
            Thread.sleep(1000)
            assertEquals(PlaybackStatus.Paused, manager.playbackState.value.status)
            assertTrue(kotlin.math.abs(manager.playbackState.value.positionMs - paused) < 200)
            main { manager.resume() }
            await("resume advances audio") { manager.playbackState.value.positionMs > paused + 200 }
            main { manager.stop() }
            stage("stopped; replace and enqueue")
            assertEquals(PlaybackStatus.Idle, manager.playbackState.value.status)
            assertFalse(manager.hasActiveReading())

            main {
                assertTrue(manager.speak("这一句应被停止。", "obsolete"))
                manager.stop()
                assertTrue(manager.speak("这是第一条连续朗读。", "first"))
                assertTrue(manager.speak("这是第二条连续朗读。", "second", flush = false))
            }
            await("queued replies finish", 90_000) { manager.playbackState.value.status == PlaybackStatus.Ended }
            stage("queue finished; missing engine")
            val snapshot = synchronized(events) { events.toList() }
            assertTrue(snapshot.contains("first" to true))
            assertTrue(snapshot.contains("second" to true))
            assertTrue(snapshot.contains("second" to false))
            assertNull(manager.engineError.value)
            assertFalse(manager.hasActiveReading())

            main {
                manager.applyConfig(MediaConfig(ttsSystemEngine = "io.cyrene.missing.test.engine"))
            }
            await("missing engine fails explicitly", 20_000) { manager.initFailedState.value }
            main { assertFalse(manager.speak("不可用引擎不能朗读。", "missing")) }
            main { manager.applyConfig(MediaConfig(ttsSystemEngine = engine, ttsLanguage = "zh-CN")) }
            await("engine recovers", 20_000) { manager.isReady.value }
            main {
                manager.applyConfig(MediaConfig(ttsEngine = "cyrene-offline", ttsVoice = "missing_pack/missing_role"))
                assertTrue(manager.speak("不存在的离线角色不得回退到云端或系统音色。", "missing-local-role"))
            }
            await("missing embedded role fails without fallback", 20_000) {
                manager.playbackState.value.status == PlaybackStatus.Ended && manager.engineError.value != null
            }
        } finally {
            stage("shutdown")
            main { manager.shutdown() }
            watchdog.shutdownNow()
        }
    }
}
