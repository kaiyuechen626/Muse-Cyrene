package io.zer0.muse.ui.speech

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SpeechPrefetchTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun laterSynthesisFailureDoesNotCutOffAlreadyPreparedSpeech() = runBlocking {
        val laterFailed = CompletableDeferred<Unit>()
        val played = mutableListOf<Int>()
        try {
            SpeechPrefetch.play(0..2, prepare = { index ->
                if (index == 2) {
                    laterFailed.complete(Unit)
                    error("later synthesis failed")
                }
                directory.newFile("$index.wav")
            }, consume = { index, _ ->
                if (index == 0) withTimeout(2000) { laterFailed.await() }
                played += index
            })
            fail("Synthesis failure must be reported after prepared speech finishes")
        } catch (expected: IllegalStateException) {
            assertEquals("later synthesis failed", expected.message)
        }
        assertEquals(listOf(0, 1), played)
        assertTrue(directory.root.listFiles()!!.isEmpty())
    }

    @Test fun queuedReplyPreparesBeforePreviousReplyFinishes() = runBlocking {
        val pending = ArrayDeque(listOf("reply1"))
        val prepared = CompletableDeferred<Unit>()
        val played = mutableListOf<String>()
        SpeechPrefetch.play(next = { pending.removeFirstOrNull() }, prepare = { reply ->
            if (reply == "reply2") prepared.complete(Unit)
            directory.newFile("$reply.wav")
        }, consume = { reply, _ ->
            played += reply
            if (reply == "reply1") {
                pending.addLast("reply2")
                withTimeout(2000) { prepared.await() }
            }
        })
        assertEquals(listOf("reply1", "reply2"), played)
        assertTrue(directory.root.listFiles()!!.isEmpty())
    }

    @Test fun preparesNextWhileCurrentPlaysAndPreservesOrder() = runBlocking {
        val nextReady = CompletableDeferred<Unit>()
        val played = mutableListOf<Int>()
        SpeechPrefetch.play(0..2, prepare = { index ->
            val file = directory.newFile("$index.wav")
            if (index == 1) nextReady.complete(Unit)
            file
        }, consume = { index, file ->
            assertTrue(file.exists())
            if (index == 0) withTimeout(2000) { nextReady.await() }
            played += index
        })
        assertEquals(listOf(0, 1, 2), played)
        assertTrue(directory.root.listFiles()!!.isEmpty())
    }

    @Test fun cancellationDiscardsQueuedAndPlayingFiles() = runBlocking {
        val playing = CompletableDeferred<Unit>()
        val job = launch {
            SpeechPrefetch.play(0..20, prepare = { directory.newFile("$it.wav") }, consume = { _, _ ->
                playing.complete(Unit)
                awaitCancellation()
            })
        }
        withTimeout(2000) { playing.await() }
        job.cancelAndJoin()
        assertTrue(directory.root.listFiles()!!.isEmpty())
    }

    @Test fun playbackFailureCleansPreparedFiles() = runBlocking {
        try {
            SpeechPrefetch.play(0..20, prepare = { directory.newFile("$it.wav") }, consume = { _, _ -> error("player failed") })
            fail("Playback failure should propagate")
        } catch (expected: IllegalStateException) { assertEquals("player failed", expected.message) }
        assertTrue(directory.root.listFiles()!!.isEmpty())
    }
}
