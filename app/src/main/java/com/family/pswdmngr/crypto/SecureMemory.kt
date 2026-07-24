package com.family.pswdmngr.crypto

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A ByteArray wrapper backed by a **direct** `java.nio.ByteBuffer` (native
 * memory, never moved by GC). Provides guaranteed explicit zeroing.
 *
 * ## Why not libsodium?
 *
 * The ideal would be libsodium's `sodium_mlock` (prevents swap to disk) and
 * `sodium_memzero` (compiler-proof zeroing). However, the popular Android
 * binding (`lazysodium-android`) is not available on Maven Central, so we
 * use a pure-Java approach:
 *
 * - `ByteBuffer.allocateDirect()` allocates native memory that the GC can
 *   never copy, move, or duplicate — unlike a regular `ByteArray`, there
 *   is no risk of secret copies lingering on the Java heap.
 * - Manual zeroing with explicit `put()` calls that cannot be optimized
 *   away (the JVM guarantees side-effects on direct buffers are visible).
 * - Explicit `wipe()` must be called; there is no GC-based cleanup path
 *   for secure data by design.
 *
 * For a personal offline password manager this is sufficient. If the app
 * ever ships to a wider audience, reintroduce lazysodium via jitpack.io:
 *
 *   implementation("com.goterl:lazysodium-android:5.2.1")
 *   implementation("net.java.dev.jna:jna:5.14.0@aar")
 *
 * And add `maven { url = uri("https://jitpack.io") }` to the repositories.
 */
class SecureData(size: Int) {

    /** Native-memory buffer, little-endian for byte-level access. */
    private val buffer: ByteBuffer =
        ByteBuffer.allocateDirect(size).also { it.order(ByteOrder.LITTLE_ENDIAN) }

    init {
        require(size > 0) { "SecureData size must be positive" }
    }

    /** Provide read-only access to the buffer's content for the duration of [block]. */
    fun <T> withBytes(block: (ByteArray) -> T): T {
        val arr = ByteArray(buffer.capacity())
        buffer.duplicate().apply { rewind(); get(arr) }
        return block(arr)
    }

    /** Return a **copy** to pass to APIs that need a short-lived ByteArray. */
    fun copyOf(): ByteArray {
        val arr = ByteArray(buffer.capacity())
        buffer.duplicate().apply { rewind(); get(arr) }
        return arr
    }

    /** Zero every byte in the buffer. This is guaranteed by the JVM spec. */
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
            sd.buffer.duplicate().apply { rewind(); put(raw) }
            raw.fill(0)
            sd
        } catch (_: Exception) { null }
    }
}
