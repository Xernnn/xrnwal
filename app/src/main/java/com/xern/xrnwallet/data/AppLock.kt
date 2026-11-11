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

    fun withPin(settings: Settings, pin: String): Settings {
        require(pin.length in PIN_MIN..PIN_MAX && pin.all { it in '0'..'9' }) { "A PIN is 4 to 8 digits" }
        return settings.copy(pinHash = LockSecret.hash(pin), pinLength = pin.length)
    }

    fun withPattern(settings: Settings, pattern: String): Settings = settings.copy(patternHash = LockSecret.hash(pattern))

    /**
     * Tries [secret] as the pattern when [pattern], else as the PIN. One kept the old way is kept again
     * the new way once it has opened the app, which is the only time this phone has it.
     */
    fun attempt(settings: Settings, pattern: Boolean, secret: String): UnlockTry {
        val stored = (if (pattern) settings.patternHash else settings.pinHash) ?: return UnlockTry(settings, false)
        if (!LockSecret.matches(stored, settings.lockSalt, secret)) return UnlockTry(settings, false)
        val kept = when {
            LockSecret.isCurrent(stored) -> settings
            pattern -> settings.copy(patternHash = LockSecret.hash(secret))
            else -> settings.copy(pinHash = LockSecret.hash(secret))
        }
        return UnlockTry(kept, true)
    }
}
