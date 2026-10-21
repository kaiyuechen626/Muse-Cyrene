package io.zer0.muse.ui.speech

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class SpeechSynthesisRequestsTest {
    @Test fun preparationDoesNotInterleaveAndObsoleteResultIsRejected() = runBlocking {
        withTimeout(2000) {
            val requests = SpeechSynthesisRequests()
            val first = requests.begin("first", 1)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val a = async { runCatching { requests.prepare(first) { entered.complete(Unit); release.await(); 1 } } }
            entered.await()
            val next = requests.begin("next", 1)
            var enteredNext = false
            val b = async { requests.prepare(next) { enteredNext = true; 2 } }
            yield()
            assertFalse(enteredNext)
            release.complete(Unit)
            assertTrue(a.await().exceptionOrNull() is CancellationException)
            assertEquals(2, b.await())
        }
    }

    @Test fun replacedRequestWaitingForPreparationNeverSubmits() = runBlocking {
        withTimeout(2000) {
            val requests = SpeechSynthesisRequests()
            val first = requests.begin("first", 1)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val a = async { runCatching { requests.prepare(first) { entered.complete(Unit); release.await() } } }
            entered.await()
            val obsolete = requests.begin("obsolete", 1)
            var submitted = false
            val b = async { runCatching { requests.prepare(obsolete) { submitted = true } } }
            yield()
            val latest = requests.begin("latest", 1)
            val c = async { requests.prepare(latest) { "latest" } }
            release.complete(Unit)
            a.await()
            assertTrue(b.await().exceptionOrNull() is CancellationException)
            assertFalse(submitted)
            assertEquals("latest", c.await())
        }
    }
    @Test fun lateCallbacksCannotCompleteReplacementRequest() {
        val requests = SpeechSynthesisRequests()
        val first = requests.begin("first", 1)
        val next = requests.begin("next", 1)
        assertTrue(first.completion.isCancelled)
        requests.complete("first", 1)
        requests.fail("first", 1)
        assertFalse(next.completion.isCompleted)
        requests.finish(first)
        assertTrue(requests.isCurrent(next))
    }

    @Test fun callbacksFromPreviousEngineAreIgnored() {
        val requests = SpeechSynthesisRequests()
        val next = requests.begin("same-id", 2)
        requests.complete("same-id", 1)
        requests.fail("same-id", 1)
        assertFalse(next.completion.isCompleted)
        requests.complete("same-id", 2)
        assertTrue(next.completion.isCompleted)
        assertFalse(next.completion.isCancelled)
    }

    @Test fun matchedFailureReachesItsWaiter() = runBlocking {
        val requests = SpeechSynthesisRequests()
        val request = requests.begin("error", 1)
        requests.fail("error", 1)
        try { request.completion.await(); fail("Error should propagate") }
        catch (expected: IllegalStateException) { assertEquals("TTS synthesis failed", expected.message) }
    }

    @Test fun stopCancelsPendingRequestAndOldCleanupCannotClearNewRequest() {
        val requests = SpeechSynthesisRequests()
        val first = requests.begin("first", 1)
        assertTrue(requests.cancel())
        assertTrue(first.completion.isCancelled)
        assertFalse(requests.cancel())
        val next = requests.begin("next", 2)
        requests.finish(first)
        assertTrue(requests.isCurrent(next))
        requests.finish(next)
        assertFalse(requests.isCurrent(next))
    }
}
