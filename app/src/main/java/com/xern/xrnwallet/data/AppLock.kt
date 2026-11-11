package com.xern.xrnwallet.data

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The app lock's PIN and pattern, kept as PBKDF2-HMAC-SHA256 with a salt of their own, so the settings
 * file alone doesn't give them away. A check takes a fraction of a second on a phone, so it runs off the
 * main thread.
 */
object LockSecret {
    /** OWASP's figure for PBKDF2-HMAC-SHA256. Kept in each hash, so a stored one says how it was made. */
    const val ITERATIONS = 600_000
    private const val KIND = "pbkdf2-sha256"
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private val random = SecureRandom()

    fun hash(secret: String): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return "$KIND:$ITERATIONS:${hex(salt)}:${hex(derive(secret, salt, ITERATIONS))}"
    }

    /**
     * Whether [secret] is the one [stored] was made from. Earlier builds kept one SHA-256 of [legacySalt]
     * and the secret, which still matches, and [isCurrent] then says to keep it again with [hash].
     */
    fun matches(stored: String, legacySalt: String, secret: String): Boolean {
        val parts = stored.split(':')
        if (parts.size != 4 || parts[0] != KIND) {
            return MessageDigest.isEqual(sha256(legacySalt + secret).toByteArray(), stored.toByteArray())
        }
        val iterations = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return false
        val salt = unhex(parts[2]) ?: return false
        val expected = unhex(parts[3]) ?: return false
        return MessageDigest.isEqual(derive(secret, salt, iterations), expected)
    }

    fun isCurrent(stored: String): Boolean = stored.startsWith("$KIND:$ITERATIONS:")

    private fun derive(secret: String, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(secret.toCharArray(), salt, iterations, KEY_BITS)).encoded

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun unhex(text: String): ByteArray? {
        if (text.isEmpty() || text.length % 2 != 0) return null
        return ByteArray(text.length / 2) { (text.substring(it * 2, it * 2 + 2).toIntOrNull(16) ?: return null).toByte() }
    }
}

/** What a try at the lock screen did: the settings to keep, and whether it opened the app. */
data class UnlockTry(val settings: Settings, val opened: Boolean)

object AppLock {
    const val PIN_MIN = 4
    const val PIN_MAX = 8
    const val FREE_TRIES = 5
    const val FIRST_WAIT_MILLIS = 30_000L

    /** About a year. Only so the doubling can't overflow. */
    private const val MOST_DOUBLINGS = 20

    fun withPin(settings: Settings, pin: String): Settings {
        require(pin.length in PIN_MIN..PIN_MAX && pin.all { it in '0'..'9' }) { "A PIN is 4 to 8 digits" }
        return settings.copy(pinHash = LockSecret.hash(pin), pinLength = pin.length)
    }

    fun withPattern(settings: Settings, pattern: String): Settings = settings.copy(patternHash = LockSecret.hash(pattern))

    /** The wait after [misses] wrong tries in a row: none for the first four, then 30 seconds, doubled with each miss after. */
    fun waitAfter(misses: Int): Long =
        if (misses < FREE_TRIES) 0 else FIRST_WAIT_MILLIS shl (misses - FREE_TRIES).coerceAtMost(MOST_DOUBLINGS)

    /** How long until the next try, at [now]. Never longer than the wait itself, so a clock set back can't stretch it. */
    fun waitLeft(settings: Settings, now: Long): Long {
        val wait = waitAfter(settings.unlockMisses)
        return (settings.lastMissAt + wait - now).coerceIn(0, wait)
    }

    /** With the clock set back past the last miss, the wait counts from [now], or it would last until the clock caught up. */
    fun anchored(settings: Settings, now: Long): Settings =
        if (settings.lastMissAt > now) settings.copy(lastMissAt = now) else settings

    /** After a way in other than a PIN or pattern, such as biometrics. */
    fun opened(settings: Settings): Settings = settings.copy(unlockMisses = 0, lastMissAt = 0)

    /**
     * Tries [secret] as the pattern when [pattern], else as the PIN, at [now]. Both count against one
     * limit, kept in the settings so closing the app doesn't reset it, and nothing is tried during a
     * wait. One kept the old way is kept again the new way once it has opened the app, which is the only
     * time this phone has it.
     */
    fun attempt(settings: Settings, pattern: Boolean, secret: String, now: Long): UnlockTry {
        if (waitLeft(settings, now) > 0) return UnlockTry(settings, false)
        val stored = (if (pattern) settings.patternHash else settings.pinHash) ?: return UnlockTry(settings, false)
        if (!LockSecret.matches(stored, settings.lockSalt, secret)) {
            return UnlockTry(settings.copy(unlockMisses = settings.unlockMisses + 1, lastMissAt = now), false)
        }
        val kept = when {
            LockSecret.isCurrent(stored) -> settings
            pattern -> settings.copy(patternHash = LockSecret.hash(secret))
            else -> settings.copy(pinHash = LockSecret.hash(secret))
        }
        return UnlockTry(opened(kept), true)
    }
}
