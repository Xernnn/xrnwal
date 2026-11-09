package com.xern.xrnwallet

import com.xern.xrnwallet.data.EarlierCopy
import com.xern.xrnwallet.data.PushResult
import com.xern.xrnwallet.data.ServerAddress
import com.xern.xrnwallet.data.ServerCopy
import com.xern.xrnwallet.data.ServerFailure
import com.xern.xrnwallet.data.Settings
import com.xern.xrnwallet.data.SyncEngine
import com.xern.xrnwallet.data.SyncMeta
import com.xern.xrnwallet.data.SyncMetaStore
import com.xern.xrnwallet.data.SyncOutcome
import com.xern.xrnwallet.data.SyncServer
import com.xern.xrnwallet.data.SyncedWallet
import com.xern.xrnwallet.data.isSafePhotoName
import com.xern.xrnwallet.data.sha256
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Behaves like server/xrnwallet-sync.py, and can lose an answer after doing the work. */
private class FakeServer : SyncServer {
    data class Kept(val version: Long, val body: String)

    var version = 0L
    var body = ""
    val history = mutableListOf<Kept>()
    val photos = mutableMapOf<String, ByteArray>()
    var writes = 0
    var loseNextAnswer = false
    var unreachable = false
    var failPhotos = false

    fun write(text: String) {
        if (version > 0) history += Kept(version, body)
        version++
        body = text
        writes++
    }

    private fun copy() = ServerCopy(version, if (version == 0L) "" else sha256(body), body, "2026-11-06T00:00:00+00:00")

    private fun reach() {
        if (unreachable) throw ServerFailure(0)
    }

    override fun current(): ServerCopy {
        reach()
        return copy()
    }

    override fun newerThan(version: Long): ServerCopy? {
        reach()
        return if (version == this.version) null else copy()
    }

    override fun push(body: String, base: String): PushResult {
        reach()
        if (base != SyncServer.ANY_VERSION && base != version.toString()) {
            return PushResult.Stale(version, sha256(this.body), "2026-11-06T00:00:00+00:00")
        }
        write(body)
        if (loseNextAnswer) {
            loseNextAnswer = false
            throw ServerFailure(0)
        }
        return PushResult.Accepted(version)
    }

    override fun earlierCopies() = history.map { EarlierCopy(it.version.toString(), it.version, "") }
    override fun earlierCopy(id: String) = history.first { it.version.toString() == id }.body
    override fun photoNames() = photos.keys.toSet()

    override fun putPhoto(name: String, file: File) {
        photos[name] = file.readBytes()
    }

    override fun getPhoto(name: String, target: File): Boolean {
        if (failPhotos) throw ServerFailure(0)
        val bytes = photos[name] ?: return false
        target.writeBytes(bytes)
        return true
    }
}

/** A wallet whose whole content is a line of text, with photos named after a `|`. */
private class FakeWallet(private val folder: File, var content: String) : SyncedWallet {
    var replaced = 0

    /** A bill saved just as a copy is about to replace this one, and one saved just after. Each happens once. */
    var beforeReplace: (() -> Unit)? = null
    var afterReplace: (() -> Unit)? = null

    override suspend fun document(): String = content

    override suspend fun replaceWith(body: String, expected: String): String? {
        beforeReplace?.also { beforeReplace = null }?.invoke()
        if (sha256(content) != expected) return null
        content = body
        replaced++
        return sha256(content).also { afterReplace?.also { afterReplace = null }?.invoke() }
    }

    override fun photosIn(body: String) = body.substringAfter('|', "").split(',').filter { it.isNotEmpty() }
    override fun photoFile(name: String) = File(folder, name)
}

private class FakeMeta : SyncMetaStore {
    var meta = SyncMeta()
    override fun load() = meta
    override fun save(meta: SyncMeta) {
        this.meta = meta
    }
}

class SyncEngineTest {
    @get:Rule val folder = TemporaryFolder()

    private val server = FakeServer()
    private val wallet by lazy { FakeWallet(folder.root, "bills") }
    private val meta = FakeMeta()
    private var syncPhotos = true
    private val engine by lazy { SyncEngine(server, wallet, meta, { syncPhotos }, { 1000L }) }

    /** A second phone already in step with the server. */
    private fun otherPhoneWrites(text: String) = server.write(text)

    private fun sync() = runBlocking { engine.sync() }

    private fun synced() {
        assertEquals(SyncOutcome.Uploaded, sync())
    }

    private fun assertConflict(outcome: SyncOutcome) = assertTrue("got $outcome", outcome is SyncOutcome.Conflict)

    @Test fun firstSyncFillsAnEmptyServer() {
        assertEquals(SyncOutcome.Uploaded, sync())
        assertEquals("bills", server.body)
        assertEquals(1L, meta.meta.version)
        assertEquals(sha256("bills"), meta.meta.hash)
        assertEquals(1000L, meta.meta.syncedAt)
    }

    @Test fun nothingChangedWritesNothing() {
        synced()
        assertEquals(SyncOutcome.UpToDate, sync())
        assertEquals(1, server.writes)
    }

    @Test fun aChangeHereGoesUp() {
        synced()
        wallet.content = "bills and more"
        assertEquals(SyncOutcome.Uploaded, sync())
        assertEquals("bills and more", server.body)
        assertEquals(2L, meta.meta.version)
    }

    @Test fun aChangeThereComesDownWhenNothingChangedHere() {
        synced()
        otherPhoneWrites("from the other phone")
        assertEquals(SyncOutcome.Downloaded, sync())
        assertEquals("from the other phone", wallet.content)
        assertEquals(2L, meta.meta.version)
        assertEquals(SyncOutcome.UpToDate, sync())
    }

    @Test fun firstSyncAgainstAnExistingCopyAsksRatherThanOverwrites() {
        server.write("the real data")
        assertConflict(sync())
        assertEquals("the real data", server.body)
        assertEquals("bills", wallet.content)
        assertEquals(0, wallet.replaced)
        assertNull(meta.meta.version)
    }

    @Test fun firstSyncAgainstAnIdenticalCopyJustJoinsIt() {
        server.write("bills")
        assertEquals(SyncOutcome.UpToDate, sync())
        assertEquals(1L, meta.meta.version)
        assertEquals(1, server.writes)
    }

    @Test fun changesOnBothSidesAskRatherThanOverwrite() {
        synced()
        otherPhoneWrites("from the other phone")
        wallet.content = "from this phone"
        assertConflict(sync())
        assertEquals("from the other phone", server.body)
        assertEquals("from this phone", wallet.content)
    }

    @Test fun aLostAnswerIsNotAConflict() {
        synced()
        wallet.content = "lunch"
        server.loseNextAnswer = true
        assertThrows(ServerFailure::class.java) { sync() }
        assertEquals("the upload did land", "lunch", server.body)
        assertEquals("but this phone can't know that", 1L, meta.meta.version)

        assertEquals(SyncOutcome.Uploaded, sync())
        assertEquals(2, server.writes)
        assertEquals(2L, meta.meta.version)
        assertTrue(meta.meta.unanswered.isEmpty())
    }

    @Test fun aLostAnswerFollowedByAnotherChangeIsNotAConflict() {
        synced()
        wallet.content = "lunch"
        server.loseNextAnswer = true
        assertThrows(ServerFailure::class.java) { sync() }
        wallet.content = "lunch and dinner"
        assertEquals(SyncOutcome.Uploaded, sync())
        assertEquals("lunch and dinner", server.body)
        assertEquals(3L, meta.meta.version)
    }

    @Test fun aLostAnswerThenAnUploadThatNeverArrivedIsNotAConflict() {
        synced()
        wallet.content = "lunch"
        server.loseNextAnswer = true
        assertThrows(ServerFailure::class.java) { sync() }
        wallet.content = "lunch and dinner"
        server.unreachable = true
        assertThrows(ServerFailure::class.java) { sync() }
        server.unreachable = false
        wallet.content = "lunch, dinner and a snack"
        assertEquals(SyncOutcome.Uploaded, sync())
        assertEquals("lunch, dinner and a snack", server.body)
    }

    @Test fun anUploadThatLandedStillCountsAfterTheChangeWasUndone() {
        synced()
        wallet.content = "a mistake"
        server.loseNextAnswer = true
        assertThrows(ServerFailure::class.java) { sync() }
        wallet.content = "bills"
        assertEquals(SyncOutcome.Uploaded, sync())
        assertEquals("the undo wins", "bills", server.body)
        assertEquals("bills", wallet.content)
    }

    @Test fun aLostFirstAnswerIsNotAConflict() {
        server.loseNextAnswer = true
        assertThrows(ServerFailure::class.java) { sync() }
        assertEquals(SyncOutcome.UpToDate, sync())
        assertEquals(1L, meta.meta.version)
        assertEquals(1, server.writes)
    }

    @Test fun aServerThatWentBackwardsIsNotFollowedSilently() {
        synced()
        wallet.content = "second"
        sync()
        server.version = 1
        server.body = "an old restore"
        assertConflict(sync())
        assertEquals("second", wallet.content)
    }

    @Test fun aWipedServerIsFilledAgain() {
        synced()
        server.version = 0
        server.body = ""
        assertEquals(SyncOutcome.Uploaded, sync())
        assertEquals("bills", server.body)
    }

    @Test fun aBillAddedDuringADownloadIsNotOverwritten() {
        synced()
        otherPhoneWrites("from the other phone")
        val added = "a bill added just now"
        wallet.beforeReplace = { wallet.content = added }
        assertConflict(sync())
        assertEquals(added, wallet.content)
        assertEquals(0, wallet.replaced)
    }

    @Test fun aBillAddedJustAfterADownloadStillGoesUp() {
        synced()
        otherPhoneWrites("from the other phone")
        val added = "from the other phone, and a bill added just after"
        wallet.afterReplace = { wallet.content = added }
        assertEquals(SyncOutcome.Downloaded, sync())
        assertEquals(SyncOutcome.Uploaded, sync())
        assertEquals(added, server.body)
    }

    @Test fun keepingThePhoneReplacesTheServerAndKeepsItsCopy() = runBlocking {
        server.write("the server's")
        assertConflict(engine.sync())
        assertEquals(SyncOutcome.Uploaded, engine.keepPhone())
        assertEquals("bills", server.body)
        assertEquals(listOf("the server's"), server.history.map { it.body })
        assertEquals(SyncOutcome.UpToDate, engine.sync())
    }

    @Test fun keepingTheServerFilesThePhonesCopyFirst() = runBlocking {
        server.write("the server's")
        assertConflict(engine.sync())
        engine.keepServer()
        assertEquals("the server's", wallet.content)
        assertEquals("the server's", server.body)
        assertTrue("bills" in server.history.map { it.body })
        assertEquals(SyncOutcome.UpToDate, engine.sync())
    }

    @Test fun aBillAddedWhileKeepingTheServersCopyGoesUpBeforeItIsReplaced() = runBlocking {
        server.write("the server's")
        assertConflict(engine.sync())
        wallet.beforeReplace = { wallet.content = "bills and one added meanwhile" }
        engine.keepServer()
        assertEquals("the server's", wallet.content)
        assertTrue("bills and one added meanwhile" in server.history.map { it.body })
    }

    @Test fun keepingTheServerChangesNothingHereIfThePhonesCopyCantGoUp() = runBlocking {
        server.write("the server's")
        assertConflict(engine.sync())
        server.loseNextAnswer = false
        val before = wallet.content
        val outcome = runCatching {
            // Reachable for the read, then gone for the upload.
            object : SyncServer by server {
                override fun push(body: String, base: String): PushResult = throw ServerFailure(0)
            }.let { SyncEngine(it, wallet, meta, { false }).keepServer() }
        }
        assertTrue(outcome.isFailure)
        assertEquals(before, wallet.content)
        assertEquals("the server's", server.body)
    }

    @Test fun anEarlierCopyComesBackAndWhatWasHereIsKept() = runBlocking {
        engine.sync()
        wallet.content = "second"
        engine.sync()
        wallet.content = "unsynced third"
        assertEquals(SyncOutcome.Uploaded, engine.restoreEarlier("1"))
        assertEquals("bills", wallet.content)
        assertEquals("bills", server.body)
        assertTrue("unsynced third" in server.history.map { it.body })
    }

    @Test fun aBillAddedWhileAnEarlierCopyComesBackGoesUpBeforeItIsReplaced() = runBlocking {
        engine.sync()
        wallet.content = "second"
        engine.sync()
        wallet.beforeReplace = { wallet.content = "second and one added meanwhile" }
        assertEquals(SyncOutcome.Uploaded, engine.restoreEarlier("1"))
        assertEquals("bills", wallet.content)
        assertTrue("second and one added meanwhile" in server.history.map { it.body })
    }

    @Test fun anEarlierCopyWaitsWhileThePhoneAndServerDiffer() = runBlocking {
        engine.sync()
        wallet.content = "second"
        engine.sync()
        otherPhoneWrites("the other phone's")
        wallet.content = "this phone's"
        assertConflict(engine.restoreEarlier("1"))
        assertEquals("this phone's", wallet.content)
    }

    @Test fun photosGoUpOnce() {
        File(folder.root, "a.jpg").writeBytes(byteArrayOf(1))
        wallet.content = "bills|a.jpg,missing.jpg"
        synced()
        assertEquals(setOf("a.jpg"), server.photos.keys)
        server.photos["a.jpg"] = byteArrayOf(9)
        wallet.content = "more bills|a.jpg"
        synced()
        assertEquals(9, server.photos["a.jpg"]!![0].toInt())
    }

    @Test fun photosStayHereWhenSyncPhotoIsOff() {
        syncPhotos = false
        File(folder.root, "a.jpg").writeBytes(byteArrayOf(1))
        wallet.content = "bills|a.jpg"
        synced()
        assertTrue(server.photos.isEmpty())
    }

    @Test fun photosComeDownBeforeThePhoneChanges() {
        synced()
        server.photos["b.jpg"] = byteArrayOf(2)
        otherPhoneWrites("new|b.jpg,gone.jpg")
        assertEquals(SyncOutcome.Downloaded, sync())
        assertEquals(2, File(folder.root, "b.jpg").readBytes()[0].toInt())
        assertFalse(File(folder.root, "gone.jpg").exists())
    }

    @Test fun aPhotoThatWontComeLeavesThePhoneAsItWas() {
        synced()
        server.photos["b.jpg"] = byteArrayOf(2)
        otherPhoneWrites("new|b.jpg")
        server.failPhotos = true
        assertThrows(ServerFailure::class.java) { sync() }
        assertEquals("bills", wallet.content)
        assertEquals(1L, meta.meta.version)
    }

    @Test fun onlyHttpsAddressesAreTaken() {
        assertTrue(ServerAddress.isValid("https://home.example.net/xrnwallet"))
        assertFalse(ServerAddress.isValid("http://home.example.net/xrnwallet"))
        assertFalse(ServerAddress.isValid("home.example.net"))
        assertFalse(ServerAddress.isValid("https://"))
        assertFalse(ServerAddress.isValid("http://10.0.2.2:5237"))
        assertTrue(ServerAddress.isValid("http://10.0.2.2:5237", emulator = true))
        assertFalse(ServerAddress.isValid("http://10.0.2.2.example.net", emulator = true))
        assertEquals("https://a.net/xrnwallet", ServerAddress.clean(" https://a.net/xrnwallet// "))
    }

    @Test fun photoNamesFromACopyCantLeaveTheFolder() {
        assertTrue(isSafePhotoName("0f8e-4c.jpg"))
        assertFalse(isSafePhotoName("../settings.xml"))
        assertFalse(isSafePhotoName(".hidden"))
        assertFalse(isSafePhotoName("a/b.jpg"))
        assertFalse(isSafePhotoName("a..jpg"))
    }

    @Test fun theHashMatchesTheServers() {
        // python3 -c "import hashlib; print(hashlib.sha256('Tiền ăn 🍜'.encode()).hexdigest())"
        assertEquals("b30e6887c8721fec94679a8375ef987e18f3adfa878e28ca8cf3e801321ffaf8", sha256("Tiền ăn 🍜"))
    }

    @Test fun theAppLockNeverLeavesThePhone() {
        val locked = Settings(pinHash = "pin", patternHash = "pattern", lockSalt = "salt", biometric = true, nickname = "Xern")
        val sent = locked.withoutLock()
        assertNull(sent.pinHash)
        assertNull(sent.patternHash)
        assertEquals("", sent.lockSalt)
        assertFalse(sent.biometric)
        assertFalse(sent.locked)
        assertEquals("Xern", sent.nickname)
    }

    @Test fun takingACopyKeepsThisPhonesLock() {
        val here = Settings(pinHash = "pin", patternHash = "pattern", lockSalt = "salt", biometric = true, nickname = "old")
        val taken = Settings(nickname = "new").withLockOf(here)
        assertEquals("new", taken.nickname)
        assertEquals("pin", taken.pinHash)
        assertEquals("pattern", taken.patternHash)
        assertEquals("salt", taken.lockSalt)
        assertTrue(taken.biometric)
    }
}
