package io.zer0.muse.ui.speech

import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Bounded, ordered overlap of synthesis and playback; owns every prepared file. */
internal object SpeechPrefetch {
    suspend fun play(
        indices: IntRange,
        prepare: suspend (Int) -> File?,
        consume: suspend (Int, File) -> Unit,
    ) {
        val iterator = indices.iterator()
        play(next = { if (iterator.hasNext()) iterator.nextInt() else null }, prepare = prepare, consume = consume)
    }

    suspend fun <T : Any> play(
        next: () -> T?,
        prepare: suspend (T) -> File?,
        consume: suspend (T, File) -> Unit,
    ) = coroutineScope {
        val ready = Channel<Pair<T, File>>(1, onUndeliveredElement = { it.second.delete() })
        val outstanding = AtomicInteger()
        val producer = launch {
            try {
                while (true) {
                    val index = next()
                    if (index == null) {
                        // A reply can arrive while the last prepared sentence is still playing.
                        if (outstanding.get() == 0) break
                        delay(25)
                        continue
                    }
                    val file = prepare(index) ?: break
                    outstanding.incrementAndGet()
                    var delivered = false
                    try { ready.send(index to file); delivered = true }
                    finally { if (!delivered) file.delete() }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Preserve the current sentence and buffered audio; report after draining.
                // Throwing from this child would cancel the player in mid-sentence.
                ready.close(failure)
            } finally { ready.close() }
        }
        try {
            for ((index, file) in ready) {
                try { consume(index, file) } finally { file.delete(); outstanding.decrementAndGet() }
            }
        } finally { producer.cancel(); ready.cancel() }
    }
}
