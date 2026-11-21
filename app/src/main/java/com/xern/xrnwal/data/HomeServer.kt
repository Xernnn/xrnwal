package com.xern.xrnwal.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.util.Base64
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ServerLogin(val address: String, val user: String, val password: String)

/** Talks to server/xrnwal-sync.py. Every call blocks, so run them off the main thread. */
class HomeServer(private val login: ServerLogin, private val device: String) : SyncServer {
    private val auth = "Basic " +
        Base64.getEncoder().encodeToString("${login.user}:${login.password}".toByteArray())
    private val json = Json { ignoreUnknownKeys = true }

    private fun <T> request(
        path: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        timeout: Int = DOCUMENT_TIMEOUT,
        answer: (HttpURLConnection) -> T,
    ): T {
        val connection = URL(login.address + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = false // a login isn't for wherever a redirect points
            connection.useCaches = false
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = timeout
            connection.setRequestProperty("Authorization", auth)
            connection.setRequestProperty("X-Device", device)
            headers.forEach(connection::setRequestProperty)
            return answer(connection)
        } catch (_: IOException) {
            throw ServerFailure(0)
        } finally {
            connection.disconnect()
        }
    }

    private fun HttpURLConnection.send(body: ByteArray) {
        doOutput = true
        // Fixed length also stops HttpURLConnection quietly sending the upload a second time.
        setFixedLengthStreamingMode(body.size)
        outputStream.use { it.write(body) }
    }

    private fun HttpURLConnection.text(): String = inputStream.bufferedReader().use { it.readText() }

    private fun HttpURLConnection.version(): Long =
        getHeaderField("ETag")?.removePrefix("W/")?.trim('"')?.toLongOrNull() ?: throw ServerFailure(responseCode)

    private fun HttpURLConnection.hash(): String = getHeaderField("X-Content-SHA256").orEmpty()
    private fun HttpURLConnection.updated(): String = getHeaderField("X-Updated").orEmpty()

    // Beside the ETag rather than in it, so a phone and a server a version apart still understand each other.
    private fun HttpURLConnection.tag(): String = getHeaderField("X-Tag").orEmpty()
    private fun HttpURLConnection.follows(): Boolean = getHeaderField("X-Follows") != "0"

    /** A server says so on its /state answers; one from before reads a gzipped body as a broken document. */
    private fun HttpURLConnection.takesGzip(): Boolean =
        getHeaderField("Accept-Encoding").orEmpty().split(',').any { it.trim().equals("gzip", ignoreCase = true) }

    private fun HttpURLConnection.copy() = ServerCopy(version(), hash(), text(), updated(), tag(), follows())

    private fun failed(connection: HttpURLConnection): Nothing = throw ServerFailure(connection.responseCode)

    private fun baseTag(tag: String) = if (tag.isEmpty()) emptyMap() else mapOf("X-Base-Tag" to tag)

    override fun current(): ServerCopy = request("/state") {
        if (it.responseCode == 200) it.copy() else failed(it)
    }

    override fun newerThan(version: Long, tag: String): ServerCopy? =
        request("/state", headers = mapOf("If-None-Match" to "\"$version\"") + baseTag(tag)) {
            when (it.responseCode) {
                200 -> it.copy()
                304 -> null
                else -> failed(it)
            }
        }

    override fun push(body: String, base: String, tag: String): PushResult {
        val ifMatch = if (base == SyncServer.ANY_VERSION) base else "\"$base\""
        val headers = mapOf("If-Match" to ifMatch, "Content-Type" to "application/json; charset=utf-8") +
            if (base == SyncServer.ANY_VERSION) emptyMap() else baseTag(tag)
        val plain = body.toByteArray()
        // Gzipped, the copy goes up in about a tenth of the bytes (168 KB was 16 KB), so far less of
        // it is still on the way when the phone's connection drops, and less mobile data goes.
        return try {
            put(headers + ("Content-Encoding" to "gzip"), gzip(plain), packed = true)
        } catch (_: TakesNoGzip) {
            put(headers, plain, packed = false)
        }
    }

    /** A server from before gzipped uploads, which couldn't read one and wrote nothing. */
    private class TakesNoGzip : Exception()

    private fun put(headers: Map<String, String>, bytes: ByteArray, packed: Boolean): PushResult =
        request("/state", "PUT", headers) {
            it.send(bytes)
            val code = it.responseCode
            when {
                code == 200 -> PushResult.Accepted(it.version(), it.tag())
                code == 412 -> PushResult.Stale(it.version(), it.hash(), it.updated(), it.tag())
                packed && (code == 400 || code == 415) && !it.takesGzip() -> throw TakesNoGzip()
                else -> failed(it)
            }
        }

    private fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(bytes.size / 8 + 64)
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    override fun earlierCopies(): List<EarlierCopy> = request("/versions") {
        if (it.responseCode == 200) json.decodeFromString<List<EarlierCopy>>(it.text()) else failed(it)
    }

    override fun earlierCopy(id: String): String = request("/versions/$id") {
        if (it.responseCode == 200) it.text() else failed(it)
    }

    override fun photoNames(): Set<String> = request("/photos") {
        if (it.responseCode == 200) json.decodeFromString<List<String>>(it.text()).toSet() else failed(it)
    }

    override fun putPhoto(name: String, file: File) {
        val headers = mapOf("Content-Type" to "application/octet-stream")
        request("/photos/$name", "PUT", headers, PHOTO_TIMEOUT) {
            it.doOutput = true
            it.setFixedLengthStreamingMode(file.length())
            file.inputStream().use { input -> it.outputStream.use(input::copyTo) }
            // A photo the server won't take stays off it. The bills still go.
            val refused = it.responseCode == 400 || it.responseCode == 413
            if (it.responseCode !in 200..299 && !refused) failed(it)
        }
    }

    override fun getPhoto(name: String, target: File): Boolean =
        request("/photos/$name", timeout = PHOTO_TIMEOUT) {
            if (it.responseCode == 404) return@request false
            if (it.responseCode != 200) failed(it)
            target.parentFile?.mkdirs()
            // Through a temporary name, so a download cut short never passes for the photo.
            val part = File(target.path + ".part")
            try {
                it.inputStream.use { input -> part.outputStream().use(input::copyTo) }
                if (!part.renameTo(target)) throw IOException("could not keep the photo")
            } finally {
                part.delete()
            }
            true
        }

    private companion object {
        const val CONNECT_TIMEOUT = 15_000
        const val DOCUMENT_TIMEOUT = 60_000
        const val PHOTO_TIMEOUT = 90_000
    }
}

/** The login and sync state of this phone. None of it is part of what gets synced. */
class SyncStore(context: Context) : SyncMetaStore {
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)

    var auto: Boolean
        get() = prefs.getBoolean(AUTO, true)
        set(value) = prefs.edit().putBoolean(AUTO, value).apply()

    var photos: Boolean
        get() = prefs.getBoolean(PHOTOS, true)
        set(value) = prefs.edit().putBoolean(PHOTOS, value).apply()

    fun login(): ServerLogin? {
        val address = prefs.getString(ADDRESS, null) ?: return null
        val user = prefs.getString(USER, null) ?: return null
        val sealed = prefs.getString(PASSWORD, null) ?: return null
        val password = runCatching { Sealed.open(sealed) }.getOrNull() ?: return null
        return ServerLogin(address, user, password)
    }

    fun saveLogin(login: ServerLogin) {
        prefs.edit()
            .putString(ADDRESS, login.address)
            .putString(USER, login.user)
            .putString(PASSWORD, Sealed.seal(login.password))
            .apply()
    }

    /** Forgets the login and which server copy this phone matched. The phone's data stays. */
    fun forget() {
        prefs.edit().clear().commit()
    }

    override fun load() = SyncMeta(
        version = if (prefs.contains(VERSION)) prefs.getLong(VERSION, 0) else null,
        hash = prefs.getString(HASH, null),
        unanswered = prefs.getString(UNANSWERED, null)?.split(",").orEmpty(),
        syncedAt = prefs.getLong(SYNCED_AT, 0),
        tag = prefs.getString(TAG, null).orEmpty(),
    )

    // commit, not apply: this has to be on disk before the upload it describes leaves the phone.
    override fun save(meta: SyncMeta) {
        val edit = prefs.edit()
        if (meta.version != null) edit.putLong(VERSION, meta.version) else edit.remove(VERSION)
        edit.putString(HASH, meta.hash)
        edit.putString(UNANSWERED, meta.unanswered.joinToString(",").ifEmpty { null })
        edit.putLong(SYNCED_AT, meta.syncedAt)
        edit.putString(TAG, meta.tag.ifEmpty { null })
        edit.commit()
    }

    private companion object {
        const val ADDRESS = "address"
        const val USER = "user"
        const val PASSWORD = "password"
        const val AUTO = "auto"
        const val PHOTOS = "photos"
        const val VERSION = "version"
        const val HASH = "hash"
        const val UNANSWERED = "unanswered"
        const val SYNCED_AT = "syncedAt"
        const val TAG = "tag"
    }
}

/** Encrypts with a key that stays inside the phone's keystore, so the file alone is no use. */
private object Sealed {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "xrnwal-sync"
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    fun seal(text: String): String {
        val cipher = Cipher.getInstance(CIPHER).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(text.toByteArray()))
    }

    fun open(sealed: String): String {
        val bytes = Base64.getDecoder().decode(sealed)
        val spec = GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES)
        val cipher = Cipher.getInstance(CIPHER).apply { init(Cipher.DECRYPT_MODE, key(), spec) }
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
    }
}
