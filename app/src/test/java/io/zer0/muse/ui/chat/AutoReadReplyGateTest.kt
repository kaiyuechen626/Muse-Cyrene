// Muse Cyrene modification notice: 2026-10-18; unofficial fork; see CYRENE_MODIFICATIONS.md (GPL-3.0-or-later).
package io.zer0.muse.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoReadReplyGateTest {
    @Test
    fun duplicateAndStaleCompletionsAreIgnored() {
        val gate = AutoReadReplyGate()
        assertTrue(gate.claim(5L, true))
        assertFalse(gate.claim(5L, true))
        assertFalse(gate.claim(4L, true))
        assertTrue(gate.claim(6L, true))
    }

    @Test
    fun disabledOrUnrelatedCompletionDoesNotConsumeGeneration() {
        val gate = AutoReadReplyGate()
        assertFalse(gate.claim(7L, false))
        assertTrue(gate.claim(7L, true))
    }
}
