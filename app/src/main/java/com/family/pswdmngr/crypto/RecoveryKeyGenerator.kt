package com.family.pswdmngr.crypto

import java.security.SecureRandom

/**
 * Generates and parses high-entropy Recovery Keys for vault access recovery.
 *
 * Format: XG7K-9PLM-2QRT-7HDN-3WSE-6FKL
 * - Base32 alphabet sans ambiguous chars (0/O, 1/I/L)
 * - 6 groups of 4 = 24 characters → ~120 bits of entropy
 * - Grouped with hyphens for readability
 */
object RecoveryKeyGenerator {

    /** 32-char alphabet: no 0, O, 1, I, L to avoid transcription errors. */
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    private const val GROUPS = 6
    private const val CHARS_PER_GROUP = 4
    private const val GROUP_SEPARATOR = '-'

    private val rng = SecureRandom()

    /** Generate a new recovery key string (e.g. "XG7K-9PLM-2QRT-7HDN-3WSE-6FKL"). */
    fun generate(): String {
        val totalChars = GROUPS * CHARS_PER_GROUP
        val chars = CharArray(totalChars) { ALPHABET[rng.nextInt(ALPHABET.length)] }
        return chars.toList()
            .chunked(CHARS_PER_GROUP)
            .joinToString(GROUP_SEPARATOR.toString()) { it.joinToString("") }
    }

    /**
     * Normalize user input: strip hyphens, whitespace, convert to uppercase.
     * Accepts both formatted ("XG7K-9PLM...") and raw ("XG7K9PLM...") input.
     */
    fun normalize(input: String): String =
        input.filter { it != GROUP_SEPARATOR && !it.isWhitespace() }.uppercase()

    /** Validate that a normalized recovery key is structurally valid. */
    fun isValid(normalized: String): Boolean {
        if (normalized.length != GROUPS * CHARS_PER_GROUP) return false
        return normalized.all { it in ALPHABET }
    }

    /** Mask a recovery key for partial display (e.g. "XG7K-••••-••••-••••-••••-6FKL"). */
    fun mask(key: String): String {
        val clean = normalize(key)
        if (clean.length < 8) return "••••"
        val groups = clean.chunked(CHARS_PER_GROUP)
        // Show first and last group, mask the rest
        return groups.mapIndexed { i, g ->
            if (i == 0 || i == groups.size - 1) g else "••••"
        }.joinToString(GROUP_SEPARATOR.toString())
    }
}
