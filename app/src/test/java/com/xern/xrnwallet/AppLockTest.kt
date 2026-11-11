package com.xern.xrnwallet

import android.content.SharedPreferences
import com.xern.xrnwallet.data.AppLock
import com.xern.xrnwallet.data.Settings
import com.xern.xrnwallet.data.SettingsStore
import com.xern.xrnwallet.data.sha256
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The phone's own preferences, kept in memory. A new [SettingsStore] on the same one is the app started again. */
class MemoryPrefs : SharedPreferences {
    private val values = HashMap<String, Any?>()

    override fun getAll(): Map<String, *> = HashMap(values)
    override fun getString(key: String, defValue: String?): String? = values[key] as String? ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = values[key] as Set<String>? ?: defValues
    override fun getInt(key: String, defValue: Int): Int = values[key] as Int? ?: defValue
    override fun getLong(key: String, defValue: Long): Long = values[key] as Long? ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = values[key] as Float? ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as Boolean? ?: defValue
    override fun contains(key: String): Boolean = key in values
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val changes = HashMap<String, Any?>()
        private var clear = false
        override fun putString(key: String, value: String?) = apply { changes[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = apply { changes[key] = values }
        override fun putInt(key: String, value: Int) = apply { changes[key] = value }
        override fun putLong(key: String, value: Long) = apply { changes[key] = value }
        override fun putFloat(key: String, value: Float) = apply { changes[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
        override fun remove(key: String) = apply { changes[key] = null }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean {
            if (clear) values.clear()
            changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            return true
        }
        override fun apply() {
            commit()
        }
    }
}

class AppLockTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun aBackupFileLeavesTheLockOutAndRestoringOneKeepsThisPhones() {
        val store = SettingsStore(MemoryPrefs())
        store.update { it.copy(nickname = "Xern", pinHash = "pinhash", patternHash = "patternhash", lockSalt = "salt", biometric = true) }
        val file = store.export()
        listOf("pinhash", "patternhash", "salt").forEach { assertFalse("$it in $file", it in file) }
        val read = Json { ignoreUnknownKeys = true }.decodeFromString<Settings>(file)
        assertFalse(read.locked)
        assertEquals("Xern", read.nickname)

        // A file made by an earlier build carries the lock of the phone that made it.
        val prefs = MemoryPrefs()
        val here = SettingsStore(prefs)
        here.update { it.copy(pinHash = "mine", lockSalt = "my salt") }
        here.restore(json.encodeToString(Settings(nickname = "From the file", pinHash = "theirs", lockSalt = "their salt", biometric = true)))
        val again = SettingsStore(prefs).settings.value
        assertEquals(listOf("From the file", "mine", "my salt"), listOf(again.nickname, again.pinHash, again.lockSalt))
        assertFalse(again.biometric)
        assertTrue(again.locked)
    }
}

class LockSecretTest {
    @Test
    fun aPinIsKeptAsPbkdf2AndOneKeptTheOldWayOpensOnceThenIsKeptTheNewWay() {
        val set = AppLock.withPin(Settings(), "12345678")
        val stored = set.pinHash.orEmpty()
        assertTrue(stored, stored.startsWith("pbkdf2-sha256:600000:"))
        assertEquals(8, set.pinLength)
        assertTrue(AppLock.attempt(set, pattern = false, secret = "12345678").opened)
        assertFalse(AppLock.attempt(set, pattern = false, secret = "1234567").opened)
        // Each is salted on its own, so two phones with one PIN keep different hashes.
        assertNotEquals(stored, AppLock.withPin(Settings(), "12345678").pinHash)

        // As an earlier build kept them: one SHA-256 of the salt and the secret.
        val old = Settings(pinHash = sha256("salt" + "4321"), patternHash = sha256("salt" + "0-1-2-5"), lockSalt = "salt")
        assertFalse(AppLock.attempt(old, pattern = false, secret = "1234").opened)
        val opened = AppLock.attempt(old, pattern = false, secret = "4321")
        assertTrue(opened.opened)
        val again = opened.settings.pinHash.orEmpty()
        assertTrue(again, again.startsWith("pbkdf2-sha256:600000:"))
        assertTrue(AppLock.attempt(opened.settings, pattern = false, secret = "4321").opened)
        assertFalse(AppLock.attempt(opened.settings, pattern = false, secret = "1234").opened)
        // The pattern, still kept the old way, opens with the salt it was made with.
        assertTrue(AppLock.attempt(opened.settings, pattern = true, secret = "0-1-2-5").opened)
        assertFalse(AppLock.attempt(opened.settings, pattern = true, secret = "0-1-2-4").opened)
    }

    @Test
    fun aPinHasFourToEightDigits() {
        assertEquals(listOf(4, 8), listOf("1234", "12345678").map { AppLock.withPin(Settings(), it).pinLength })
        listOf("123", "123456789", "12a4").forEach { pin ->
            assertTrue(pin, runCatching { AppLock.withPin(Settings(), pin) }.isFailure)
        }
    }
}
