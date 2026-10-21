package io.zer0.muse.ui.speech

import org.junit.Assert.*
import org.junit.Test

class LocalSpeechChunkerTest {
    @Test fun adjacentShortSentencesShareOneGeneration() {
        val text = "你好，今天继续。下一句在后台生成！最后一句。"
        assertEquals(listOf(text), LocalSpeechChunker.chunk(text))
    }

    @Test fun groupsEndAtSentenceBoundariesAndStayBounded() {
        val sentence = "这是一句完整的中文测试句子。"
        val text = sentence.repeat(18)
        val chunks = LocalSpeechChunker.chunk(text)
        assertTrue(chunks.size > 2)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.all { it.endsWith("。") && it.length <= TextChunker.DEFAULT_MAX_CHUNK_LENGTH })
        assertTrue(chunks.dropLast(1).all { it.length >= 40 })
    }

    @Test fun repeatedSentenceMarksStayWithTheirSentence() {
        val text = "你好".repeat(25) + "！！"
        assertEquals(listOf(text), LocalSpeechChunker.chunk(text))
    }

    @Test fun longUnpunctuatedTextIsBoundedAndPreserved() {
        val text = "这是一段没有标点的中文".repeat(30)
        val chunks = LocalSpeechChunker.chunk(text)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.all { it.length <= TextChunker.DEFAULT_MAX_CHUNK_LENGTH })
        assertTrue(LocalSpeechChunker.chunk("  \n").isEmpty())
    }

    @Test fun ordinarySentenceIsNotCutAtAnInternalComma() {
        val sentence = "这是一句话的前半部分，".repeat(6) + "这里才是这句话的结尾。"
        assertTrue(sentence.length > 60)
        assertEquals(listOf(sentence), LocalSpeechChunker.chunk(sentence))
    }
}
