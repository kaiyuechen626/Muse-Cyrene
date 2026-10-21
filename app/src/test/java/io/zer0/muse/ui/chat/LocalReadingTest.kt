package io.zer0.muse.ui.chat

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.zer0.muse.data.MediaConfig
import io.zer0.muse.data.SettingsRepository
import io.zer0.muse.ui.ChatUiState
import io.zer0.muse.ui.speech.TtsManager
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class LocalReadingTest {
    @Test
    fun `completed replies queue but manual selection interrupts using pausable playback`() {
        val tts = mockk<TtsManager>(relaxed = true)
        every { tts.speak(any(), any(), any()) } returns true
        val coordinator = ChatAudioCoordinator(
            InMemoryChatStateAccessor(), tts, mockk<SettingsRepository>(), mockk<Context>(),
        )
        val first = Uuid.random()
        val second = Uuid.random()
        coordinator.toggleTts(first, "第一条中文回复。", {}, enqueue = true)
        coordinator.toggleTts(second, "第二条中文回复。", {}, enqueue = true)
        coordinator.toggleTts(first, "手动朗读。", {})
        verify { tts.speak("第一条中文回复。", first.toString(), false) }
        verify { tts.speak("第二条中文回复。", second.toString(), false) }
        verify { tts.speak("手动朗读。", first.toString(), true) }
        verify(exactly = 0) { tts.speakStream(any(), any()) }
    }

    @Test
    fun `same message stops and unavailable engine reports failure`() {
        val id = Uuid.random()
        val tts = mockk<TtsManager>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        val coordinator = ChatAudioCoordinator(
            InMemoryChatStateAccessor(ChatUiState(speakingMessageId = id)),
            tts, mockk<SettingsRepository>(), context,
        )
        coordinator.toggleTts(id, "停止", {})
        verify { tts.stop() }
        var failed = false
        coordinator.toggleTts(Uuid.random(), "不可用", { failed = true })
        assertTrue(failed)
    }

    @Test
    fun `old settings keep default engine and new engine survives serialization`() {
        val old = Json.decodeFromString<MediaConfig>("{}")
        assertEquals("", old.ttsSystemEngine)
        val selected = old.copy(ttsSystemEngine = "test.local.engine", ttsVoiceName = "test-voice")
        assertEquals(selected, Json.decodeFromString<MediaConfig>(Json.encodeToString(MediaConfig.serializer(), selected)))
    }
}
