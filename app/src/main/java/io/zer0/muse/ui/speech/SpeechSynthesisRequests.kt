package io.zer0.muse.ui.speech

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A callback completes the request it matched, never a later request's deferred. */
internal class SpeechSynthesisRequests {
    class Request(val id: String, val generation: Long) {
        val completion = CompletableDeferred<Unit>()
    }

    private val active = AtomicReference<Request?>()
    private val preparation = Mutex()

    /** One engine's property changes and submission cannot interleave between requests. */
    suspend fun <T> prepare(request: Request, action: suspend () -> T): T = preparation.withLock {
        kotlin.coroutines.coroutineContext.ensureActive()
        if (!isCurrent(request)) throw CancellationException("Speech request replaced")
        val result = action()
        if (!isCurrent(request)) throw CancellationException("Speech request replaced")
        result
    }

    fun begin(id: String, generation: Long): Request {
        val request = Request(id, generation)
        active.getAndSet(request)?.completion?.cancel()
        return request
    }

    fun complete(id: String, generation: Long) {
        val request = active.get() ?: return
        if (request.id == id && request.generation == generation) request.completion.complete(Unit)
    }

    fun fail(id: String, generation: Long) {
        val request = active.get() ?: return
        if (request.id == id && request.generation == generation) {
            request.completion.completeExceptionally(IllegalStateException("TTS synthesis failed"))
        }
    }

    fun isCurrent(request: Request): Boolean = active.get() === request

    fun finish(request: Request) { active.compareAndSet(request, null) }

    fun cancel(): Boolean {
        val request = active.getAndSet(null) ?: return false
        request.completion.cancel()
        return true
    }
}
