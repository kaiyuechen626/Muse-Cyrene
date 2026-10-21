package io.zer0.muse.ui.speech

/** Amortize cloned-voice reference processing without cutting ordinary sentences. */
internal object LocalSpeechChunker {
    private const val TARGET_LENGTH = 40
    private val end = Regex("(?<=[。！？!?；;\\n])(?![。！？!?；;\\n])")

    fun chunk(text: String): List<String> {
        val pieces = text.split(end).filter { it.isNotBlank() }
            .flatMap { TextChunker.chunk(it, TextChunker.DEFAULT_MAX_CHUNK_LENGTH) }
        val result = mutableListOf<String>()
        val pending = StringBuilder()
        fun flush() {
            if (pending.isNotEmpty()) { result.add(pending.toString()); pending.clear() }
        }
        for (piece in pieces) {
            if (pending.length + piece.length > TextChunker.DEFAULT_MAX_CHUNK_LENGTH) flush()
            pending.append(piece)
            if (pending.length >= TARGET_LENGTH) flush()
        }
        flush()
        return result
    }
}
