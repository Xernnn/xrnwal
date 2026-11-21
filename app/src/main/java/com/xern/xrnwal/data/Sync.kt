package com.xern.xrnwal.data

import kotlinx.serialization.Serializable
import java.io.File
import java.security.MessageDigest

/** The server copy this phone last matched, and what may have reached it since. */
data class SyncMeta(
    val version: Long? = null,
    val hash: String? = null,
    /** Uploads sent whose answer never came. Any of them may be what the server now holds. */
    val unanswered: List<String> = emptyList(),
    val syncedAt: Long = 0,
    /**
     * The server's own name for that copy, new with every write. A version number comes back after the
     * server's data is restored from a backup, naming another copy. Empty from a server too old to say.
     */
    val tag: String = "",
)

/** [follows] is false when the server says this isn't the copy this phone matched, or one written on top of it. */
data class ServerCopy(
    val version: Long,
    val hash: String,
    val body: String,
    val updated: String,
    val tag: String = "",
    val follows: Boolean = true,
)

sealed interface PushResult {
    data class Accepted(val version: Long, val tag: String = "") : PushResult

    /** Someone wrote since the version this upload was based on. Nothing changed on the server. */
    data class Stale(val version: Long, val hash: String, val updated: String, val tag: String = "") : PushResult
}

@Serializable
data class EarlierCopy(val id: String, val version: Long, val updated: String, val by: String = "", val bills: Int = 0)

sealed interface SyncOutcome {
    data object UpToDate : SyncOutcome
    data object Uploaded : SyncOutcome
    data object Downloaded : SyncOutcome

    /** The phone and the server each hold something the other lacks. Only a person can choose. */
    data class Conflict(val updated: String) : SyncOutcome
}

class ServerFailure(val status: Int) : Exception(failureText(status)) {
    /** Worth trying again by itself, unlike a wrong password. */
    val temporary: Boolean get() = status == 0 || status >= 500
}

fun failureText(status: Int): String = when {
    status == 401 -> "Wrong user name or password."
    status == 429 -> "Too many wrong passwords. Try again in 15 minutes."
    status == 404 -> "Nothing answers at that address. Check it ends in /xrnwal."
    status == 413 -> "Too large for the home server."
    status >= 500 -> "The home server had a problem. Try again later."
    status != 0 -> "The home server answered $status."
    else -> "Can't reach the home server. Check the phone is online."
}

object ServerAddress {
    fun clean(text: String): String = text.trim().trimEnd('/')

    /** A password never goes out in the clear. [emulator] allows the host machine, for testing. */
    fun isValid(address: String, emulator: Boolean = false): Boolean =
        Regex("https://[^/\\s]+\\S*", RegexOption.IGNORE_CASE).matches(address) ||
            (emulator && Regex("http://10\\.0\\.2\\.2(:\\d+)?(/\\S*)?").matches(address))

    /**
     * [address] at the server's path since the app was renamed: one ending in /xrnwallet, as set up
     * before, ends in /xrnwal. The home server answers at the old path only until it is taken off.
     */
    fun renamed(address: String): String =
        if (address.endsWith(OLD_PATH)) address.dropLast(OLD_PATH.length) + PATH else address

    private const val OLD_PATH = "/xrnwallet"
    private const val PATH = "/xrnwal"
}

/** The same rule as the server's: a plain file name, so a copy can't name a path on this phone. */
fun isSafePhotoName(name: String): Boolean =
    Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}").matches(name) && ".." !in name

fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

interface SyncServer {
    fun current(): ServerCopy

    /** The current copy, or null while the server is still on [version], the copy [tag] names when not empty. */
    fun newerThan(version: Long, tag: String): ServerCopy?

    /**
     * [base] is a version, or [ANY_VERSION] to replace whatever is there. When not empty, [tag] must
     * name the copy at [base] too.
     */
    fun push(body: String, base: String, tag: String): PushResult
    fun earlierCopies(): List<EarlierCopy>
    fun earlierCopy(id: String): String
    fun photoNames(): Set<String>
    fun putPhoto(name: String, file: File)

    /** False when the server has no such photo. */
    fun getPhoto(name: String, target: File): Boolean

    companion object {
        const val ANY_VERSION = "*"
    }
}

interface SyncedWallet {
    suspend fun document(): String

    /**
     * Replaces this phone's data with [body] if its document still hashes to [expected], and returns
     * the new document's hash. Null, replacing nothing, when something was saved here first. The check,
     * the change and the new hash are one step, so no bill saved meanwhile is lost or taken as synced.
     */
    suspend fun replaceWith(body: String, expected: String): String?
    fun photosIn(body: String): List<String>
    fun photoFile(name: String): File
}

interface SyncMetaStore {
    fun load(): SyncMeta
    fun save(meta: SyncMeta)
}

/**
 * Keeps the phone and the server's copy matching, one whole document at a time.
 *
 * The rules that matter: nothing on either side is overwritten unless it is already on the other
 * side, an upload whose answer was lost isn't mistaken for someone else's write, and when the two
 * really differ a person chooses, with the side they don't choose filed on the server first.
 */
class SyncEngine(
    private val server: SyncServer,
    private val wallet: SyncedWallet,
    private val meta: SyncMetaStore,
    private val photos: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun sync(): SyncOutcome {
        val body = wallet.document()
        val hash = sha256(body)
        val state = meta.load()
        val base = state.version ?: return firstSync(body, hash, state)
        if (hash != state.hash) return upload(body, hash, state, base.toString())
        val copy = server.newerThan(base, state.tag)
        return when {
            copy == null -> {
                meta.save(state.copy(unanswered = emptyList(), syncedAt = clock()))
                SyncOutcome.UpToDate
            }
            // The server lost everything, so it's only owed what this phone has.
            copy.version == 0L -> upload(body, hash, state, "0")
            // It went backwards, restored from a backup perhaps. Don't follow it silently.
            copy.version < base -> SyncOutcome.Conflict(copy.updated)
            // A change that was then undone here reached the server. The undo is the newer one.
            copy.hash in state.unanswered ->
                upload(body, hash, SyncMeta(copy.version, copy.hash, tag = copy.tag), copy.version.toString())
            // It went backwards and was written to again, so its numbers may have caught up with this
            // phone's. What this phone matched may now be only here.
            !copy.follows -> SyncOutcome.Conflict(copy.updated)
            else -> download(copy, hash)
        }
    }

    /** The side a person chose: this phone's copy replaces the server's, which stays in its history. */
    suspend fun keepPhone(): SyncOutcome {
        val body = wallet.document()
        return upload(body, sha256(body), meta.load(), SyncServer.ANY_VERSION)
    }

    /**
     * The side a person chose: the server's copy replaces this phone's. The phone's goes up first,
     * so the wrong choice costs nothing, then the next sync puts the server's back on top.
     */
    suspend fun keepServer(): SyncOutcome {
        val copy = server.current()
        if (copy.version == 0L) return sync()
        if (photos()) fetchPhotos(copy.body)
        // A bill saved while the phone's copy went up isn't in it, so that goes up again first.
        do keepPhone() while (wallet.replaceWith(copy.body, meta.load().hash.orEmpty()) == null)
        return sync()
    }

    /** Brings back a copy the server kept. Whatever is here now goes up first, so it stays too. */
    suspend fun restoreEarlier(id: String): SyncOutcome {
        val before = sync()
        if (before is SyncOutcome.Conflict) return before
        val body = server.earlierCopy(id)
        if (photos()) fetchPhotos(body)
        // The same for a bill saved since that sync.
        while (wallet.replaceWith(body, meta.load().hash.orEmpty()) == null) {
            val again = sync()
            if (again is SyncOutcome.Conflict) return again
        }
        return sync()
    }

    /**
     * For a change that replaces this phone's data on purpose: restoring a file, or deleting
     * everything. The result is no edit of the copy this phone last matched, and sent up as one it
     * would replace the server's and so every other phone's, newer changes and all. So this phone
     * forgets that copy, as a new phone has none, and the next sync asks which to keep.
     */
    suspend fun replaceHere(change: suspend () -> Unit) {
        meta.save(SyncMeta(syncedAt = meta.load().syncedAt))
        change()
    }

    private suspend fun firstSync(body: String, hash: String, state: SyncMeta): SyncOutcome {
        val copy = server.current()
        return when {
            copy.version == 0L -> upload(body, hash, state, "0")
            copy.hash == hash -> {
                meta.save(SyncMeta(copy.version, hash, syncedAt = clock(), tag = copy.tag))
                SyncOutcome.UpToDate
            }
            else -> SyncOutcome.Conflict(copy.updated)
        }
    }

    private suspend fun upload(body: String, hash: String, state: SyncMeta, base: String): SyncOutcome {
        if (photos()) sendPhotos(body)
        // Saved before sending: the answer can be lost, or the app killed, at any point after this.
        meta.save(state.copy(unanswered = (state.unanswered + hash).distinct().takeLast(MAX_UNANSWERED)))
        return when (val answer = server.push(body, base, state.tag)) {
            is PushResult.Accepted -> {
                meta.save(SyncMeta(answer.version, hash, syncedAt = clock(), tag = answer.tag))
                SyncOutcome.Uploaded
            }
            is PushResult.Stale -> {
                if (answer.hash != hash && answer.hash !in state.unanswered) return SyncOutcome.Conflict(answer.updated)
                // That's an earlier upload of ours whose answer was lost, so carry on from it.
                val caughtUp = SyncMeta(answer.version, answer.hash, syncedAt = clock(), tag = answer.tag)
                meta.save(caughtUp)
                if (answer.hash == hash) SyncOutcome.Uploaded else upload(body, hash, caughtUp, answer.version.toString())
            }
        }
    }

    private suspend fun download(copy: ServerCopy, expected: String): SyncOutcome {
        // First, so a photo that won't come leaves this phone as it was.
        if (photos()) fetchPhotos(copy.body)
        // That can take a while, and a bill added meanwhile must not be overwritten.
        val now = wallet.replaceWith(copy.body, expected) ?: return SyncOutcome.Conflict(copy.updated)
        meta.save(SyncMeta(copy.version, now, syncedAt = clock(), tag = copy.tag))
        return SyncOutcome.Downloaded
    }

    private fun sendPhotos(body: String) {
        val names = wallet.photosIn(body).filter { wallet.photoFile(it).exists() }
        if (names.isEmpty()) return
        val have = server.photoNames()
        names.filterNot(have::contains).forEach { server.putPhoto(it, wallet.photoFile(it)) }
    }

    private fun fetchPhotos(body: String) {
        wallet.photosIn(body).filterNot { wallet.photoFile(it).exists() }
            .forEach { server.getPhoto(it, wallet.photoFile(it)) }
    }

    private companion object {
        const val MAX_UNANSWERED = 20
    }
}
