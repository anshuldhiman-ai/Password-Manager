package com.family.pswdmngr.crypto

import java.security.SecureRandom

object PasswordGenerator {

    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#\$%^&*()-_=+[]{};:,.<>?"

    private val rng = SecureRandom()

    fun generate(
        length: Int = 20,
        upper: Boolean = true,
        digits: Boolean = true,
        symbols: Boolean = true,
    ): String {
        val pools = buildList {
            add(LOWER)
            if (upper) add(UPPER)
            if (digits) add(DIGITS)
            if (symbols) add(SYMBOLS)
        }
        val all = pools.joinToString("")
        val chars = CharArray(length)
        // guarantee at least one char from each enabled pool
        pools.forEachIndexed { i, pool -> chars[i] = pool[rng.nextInt(pool.length)] }
        for (i in pools.size until length) chars[i] = all[rng.nextInt(all.length)]
        // Fisher-Yates shuffle
        for (i in length - 1 downTo 1) {
            val j = rng.nextInt(i + 1)
            val t = chars[i]; chars[i] = chars[j]; chars[j] = t
        }
        return String(chars)
    }

    private val WORDS = listOf(
        "amber","basil","cedar","delta","ember","fjord","grove","hazel","iris","juno",
        "koala","lunar","maple","nova","orbit","pearl","quartz","raven","sage","tiger",
        "ultra","velvet","willow","xenon","yodel","zephyr","atlas","bloom","coral","drift",
        "echo","flint","gleam","harbor","ivory","jade","kite","lotus","mint","nimbus",
        "onyx","pine","quill","ridge","storm","thorn","umber","vista","wren","zinc"
    )

    fun passphrase(words: Int = 5, separator: String = "-"): String =
        (1..words).joinToString(separator) { WORDS[rng.nextInt(WORDS.size)] } +
                separator + rng.nextInt(100)

    /** Rough entropy estimate in bits for strength meter. */
    fun entropy(password: String): Double {
        var pool = 0
        if (password.any { it.isLowerCase() }) pool += 26
        if (password.any { it.isUpperCase() }) pool += 26
        if (password.any { it.isDigit() }) pool += 10
        if (password.any { !it.isLetterOrDigit() }) pool += 25
        if (pool == 0) return 0.0
        return password.length * (Math.log(pool.toDouble()) / Math.log(2.0))
    }
}
