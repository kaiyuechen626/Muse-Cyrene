// Muse Cyrene modification notice: 2026-10-18; unofficial fork; see CYRENE_MODIFICATIONS.md (GPL-3.0-or-later).
package io.zer0.muse.ui.chat

/** Called on the main dispatcher. A successful generation may be read at most once. */
internal class AutoReadReplyGate {
    private var lastClaimedSerial = Long.MIN_VALUE

    fun claim(serial: Long, eligible: Boolean): Boolean {
        if (!eligible || serial <= lastClaimedSerial) return false
        lastClaimedSerial = serial
        return true
    }
}
