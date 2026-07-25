package com.family.pswdmngr.crypto

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A ByteArray wrapper backed by a **direct** `java.nio.ByteBuffer` (native
 * memory, never moved by GC). Provides guaranteed explicit zeroing.
 */
class SecureData(size: Int) {

    /** Native-memory buffer, little-endian for byte-level access. */
    private val buffer: ByteBuffer =
        ByteBuffer.allocateDirect(size).also { it.order(ByteOrder.LITTLE_ENDIAN) }

    init {
        require(size > 0) { "SecureData size must be positive" }
    }

    /** Write [src] into the secure buffer. The buffer must be the same size. */
    fun writeFrom(src: ByteArray) {
        require(src.size == buffer.capacity()) { "size mismatch: ${src.size} vs ${buffer.capacity()}" }
        buffer.duplicate().apply { rewind(); put(src) }
    }

    /** Provide read-only access to the buffer's content for the duration of [block]. */
    fun <T> withBytes(block: (ByteArray) -> T): T {
        val arr = copyOf()
        return block(arr)
    }

    /** Return a **copy** to pass to APIs that need a short-lived ByteArray. */
    fun copyOf(): ByteArray {
        val arr = ByteArray(buffer.capacity())
        buffer.duplicate().apply { rewind(); get(arr) }
        return arr
    }

    /** Zero every byte in the buffer. */
    fun wipe() {
        buffer.duplicate().apply {
            rewind()
            while (hasRemaining()) put(0.toByte())
        }
    }

    /** Encode to Base64 (for storage in SharedPreferences). */
    fun toBase64(): String = Base64.encodeToString(copyOf(), Base64.NO_WRAP)

    companion object {
        /** Decode from Base64 and wrap in SecureData. */
        fun fromBase64(b64: String): SecureData? = try {
            val raw = Base64.decode(b64, Base64.NO_WRAP)
            val sd = SecureData(raw.size)
            sd.writeFrom(raw)
            raw.fill(0)
            sd
        } catch (_: Exception) { null }
    }
}
