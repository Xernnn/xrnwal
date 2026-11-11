package com.xern.xrnwallet

import android.content.SharedPreferences
import com.xern.xrnwallet.data.Settings
import com.xern.xrnwallet.data.SettingsStore
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
