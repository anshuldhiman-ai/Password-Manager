package com.family.pswdmngr.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and

/** RFC 6238 TOTP (SHA-1, 6 digits, 30s step — the standard used by Google/GitHub/etc.) */
object Totp {

    fun code(base32Secret: String, timeMillis: Long = System.currentTimeMillis()): String {
        val key = base32Decode(base32Secret.replace(" ", "").uppercase())
        val counter = timeMillis / 1000 / 30
        val msg = ByteArray(8)
        var v = counter
        for (i in 7 downTo 0) { msg[i] = (v and 0xFF).toByte(); v = v ushr 8 }

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(msg)

        val offset = (hash[hash.size - 1] and 0x0F).toInt()
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)
        return "%06d".format(binary % 1_000_000)
    }

    /** Seconds remaining in the current 30s window. */
    fun secondsLeft(timeMillis: Long = System.currentTimeMillis()): Int =
        30 - ((timeMillis / 1000) % 30).toInt()

    /** Parse an otpauth:// URI (from QR codes) into the base32 secret, or null. */
    fun secretFromUri(uri: String): String? {
        if (!uri.startsWith("otpauth://totp", ignoreCase = true)) return null
        return Regex("[?&]secret=([A-Za-z2-7=]+)").find(uri)?.groupValues?.get(1)
    }

    fun isValidSecret(s: String): Boolean = try {
        base32Decode(s.replace(" ", "").uppercase()).isNotEmpty()
    } catch (_: Exception) { false }

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    private fun base32Decode(s: String): ByteArray {
        val clean = s.trimEnd('=')
        val out = ArrayList<Byte>(clean.length * 5 / 8)
        var buffer = 0
        var bits = 0
        for (c in clean) {
            val idx = ALPHABET.indexOf(c)
            require(idx >= 0) { "invalid base32 char" }
            buffer = (buffer shl 5) or idx
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.add(((buffer shr bits) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }
}
