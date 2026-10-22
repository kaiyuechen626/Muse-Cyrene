// SPDX-License-Identifier: GPL-3.0-or-later
package io.zer0.muse.sync

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@androidx.annotation.Keep
object LanSyncCrypto {
    fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    fun decode(text: String): ByteArray = Base64.decode(text, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    fun canonical(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canonical(value.opt(it)) }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.opt(it)) }
        is Number -> BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
        is Boolean -> value.toString()
        else -> JSONObject.quote(value.toString())
    }
    fun hash(value: Any?): String = MessageDigest.getInstance("SHA-256").digest(canonical(value).toByteArray()).joinToString("") { "%02x".format(it) }
    fun seal(key: String, aad: String, value: JSONObject): JSONObject {
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(decode(key), "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad.toByteArray())
        val bytes = cipher.doFinal(value.toString().toByteArray())
        return JSONObject().put("nonce", encode(nonce)).put("data", encode(bytes.copyOfRange(0, bytes.size - 16))).put("tag", encode(bytes.copyOfRange(bytes.size - 16, bytes.size)))
    }
    fun open(key: String, aad: String, envelope: JSONObject): JSONObject {
        val nonce = decode(envelope.getString("nonce")); require(nonce.size == 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(decode(key), "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad.toByteArray())
        return JSONObject(cipher.doFinal(decode(envelope.getString("data")) + decode(envelope.getString("tag"))).toString(Charsets.UTF_8))
    }
    fun proof(key: String, text: String): String = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(decode(key), "HmacSHA256")); encode(doFinal(text.toByteArray())) }
}
