package com.xern.xrnwal

import com.xern.xrnwal.data.Account
import com.xern.xrnwal.data.Backup
import com.xern.xrnwal.data.BackupReader
import com.xern.xrnwal.data.BillType
import com.xern.xrnwal.data.Category
import com.xern.xrnwal.data.EarlierCopy
import com.xern.xrnwal.data.Ledger
import com.xern.xrnwal.data.PushResult
import com.xern.xrnwal.data.ServerAddress
import com.xern.xrnwal.data.ServerCopy
import com.xern.xrnwal.data.ServerFailure
import com.xern.xrnwal.data.Settings
import com.xern.xrnwal.data.SyncEngine
import com.xern.xrnwal.data.SyncMeta
import com.xern.xrnwal.data.SyncMetaStore
import com.xern.xrnwal.data.SyncOutcome
import com.xern.xrnwal.data.SyncServer
import com.xern.xrnwal.data.SyncStore
import com.xern.xrnwal.data.SyncedWallet
import com.xern.xrnwal.data.isSafePhotoName
import com.xern.xrnwal.data.sha256
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Behaves like server/xrnwal-sync.py, and can lose an answer after doing the work. */
private class FakeServer : SyncServer {
    data class Kept(val version: Long, val body: String)

    var version = 0L
    var body = ""

    /** This copy's tag, new with every write, and the tags of the copies it was written on top of. */
    var tag = ""
    var trail = listOf<String>()

    /** Off for a server from before tags, which goes by version numbers alone. */
    var tagged = true
    private var tags = 0
    val history = mutableListOf<Kept>()
    val photos = mutableMapOf<String, ByteArray>()
    var writes = 0
    var loseNextAnswer = false
    var unreachable = false
    var failPhotos = false

    fun write(text: String) {
        if (version > 0) history += Kept(version, body)
        trail = if (version == 0L) emptyList() else (trail + tag).filter { it.isNotEmpty() }
        version++
        body = text
        tag = if (tagged) "tag${++tags}" else ""
        writes++
    }

    /** What restoring the server's data from a backup puts back. */
    data class Saved(val version: Long, val body: String, val tag: String, val trail: List<String>)

    fun saved() = Saved(version, body, tag, trail)

    fun restore(saved: Saved) {
        version = saved.version
        body = saved.body
        tag = saved.tag
        trail = saved.trail
    }

    private fun here() = if (version == 0L) "" else tag

    /** A phone's tag only counts when both sides have one. */
    private fun sameCopy(tag: String) = tag.isEmpty() || here().isEmpty() || tag == here()

    private fun copy(follows: Boolean = true) =
        ServerCopy(version, if (version == 0L) "" else sha256(body), body, "2026-11-06T00:00:00+00:00", here(), follows)

    private fun reach() {
        if (unreachable) throw ServerFailure(0)
    }

    override fun current(): ServerCopy {
        reach()
        return copy()
    }

    override fun newerThan(version: Long, tag: String): ServerCopy? {
        reach()
        if (version == this.version && sameCopy(tag)) return null
        return copy(follows = sameCopy(tag) || tag in trail)
    }

    override fun push(body: String, base: String, tag: String): PushResult {
        reach()
        if (base != SyncServer.ANY_VERSION && (base != version.toString() || !sameCopy(tag))) {
            return PushResult.Stale(version, sha256(this.body), "2026-11-06T00:00:00+00:00", here())
        }
        write(body)
        if (loseNextAnswer) {
            loseNextAnswer = false
            throw ServerFailure(0)
        }
        return PushResult.Accepted(version, here())
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

/** A wallet whose whole content is a line of text, with photos named after a `|` unless [names] says otherwise. */
private class FakeWallet(
    private val folder: File,
    var content: String,
    private val names: (String) -> List<String> = { it.substringAfter('|', "").split(',').filter(String::isNotEmpty) },
) : SyncedWallet {
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

    override fun photosIn(body: String) = names(body)
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

    @Test fun aServerRestoredAndWrittenBackUpToThisPhonesVersionIsNotTakenForItsCopy() {
        synced()
        val backup = server.saved()
        wallet.content = "lunch"
        synced()
        server.restore(backup)
        otherPhoneWrites("the other phone's dinner")
        // Version 2 again, but not the version 2 this phone matched.
        assertConflict(sync())
        assertEquals("lunch", wallet.content)
        assertEquals("the other phone's dinner", server.body)
    }

    @Test fun aChangeHereDoesNotOverwriteWhatWasWrittenSinceTheServerWasRestored() {
        synced()
        val backup = server.saved()
        wallet.content = "lunch"
        synced()
        server.restore(backup)
        otherPhoneWrites("the other phone's dinner")
        wallet.content = "lunch and a coffee"
        assertConflict(sync())
        assertEquals("the other phone's dinner", server.body)
    }

    @Test fun aServerRestoredAndWrittenPastThisPhonesVersionIsNotFollowedSilently() {
        synced()
        val backup = server.saved()
        wallet.content = "lunch"
        synced()
        server.restore(backup)
        otherPhoneWrites("the other phone's dinner")
        otherPhoneWrites("the other phone's breakfast")
        // Only this phone still has lunch, which the restore took off the server.
        assertConflict(sync())
        assertEquals("lunch", wallet.content)
    }

    @Test fun aServerFromBeforeTagsStillSyncsByVersion() {
        server.tagged = false
        synced()
        otherPhoneWrites("from the other phone")
        assertEquals(SyncOutcome.Downloaded, sync())
        wallet.content = "and back"
        assertEquals(SyncOutcome.Uploaded, sync())
        assertEquals("and back", server.body)
        assertEquals(SyncOutcome.UpToDate, sync())
        assertEquals("", meta.meta.tag)
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

    @Test fun aFreshInstallMeetingTheCopyXrnwalletLeftAsksThenTakesItWithItsPhoto() = runBlocking {
        // Sent by xrnwallet 0.5.3, before the rename, from a made-up wallet. Its photo is on the server too.
        val old = javaClass.getResource("/old-app/server-copy-0.5.3.json")!!.readText()
        server.write(old)
        server.photos["1731900000000-4f2a.jpg"] = byteArrayOf(1, 2, 3)
        val json = Json { encodeDefaults = true }
        // What a new install holds before it first syncs: the ledger, the account and categories it starts with.
        val fresh = json.encodeToString(
            Backup(
                settings = json.encodeToString(Settings()), ledgers = listOf(Ledger(id = 1, name = "General")),
                accounts = listOf(Account(id = 1, name = "Cash", icon = "💵")),
                categories = listOf(Category(id = 1, type = BillType.EXPENSE, name = "Food", icon = "🍜")),
                tags = emptyList(), bills = emptyList(), billTags = emptyList(), budgets = emptyList(), recurring = emptyList(),
                plans = emptyList(), deposits = emptyList(), checklists = emptyList(), checklistItems = emptyList(),
                reminders = emptyList(), rates = emptyList(),
            ),
        )
        val phone = FakeWallet(folder.root, fresh, BackupReader::photoNames)
        val newApp = SyncEngine(server, phone, meta, { true }, { 1000L })
        // Connecting asks, and "Use the server's copy" takes it.
        assertConflict(newApp.sync())
        assertEquals(old, server.body)
        assertEquals(fresh, phone.content)
        newApp.keepServer()
        assertEquals(old, phone.content)
        assertEquals(listOf("Lunch", "Opal top-up", "Café shifts", "Phở", ""), BackupReader.read(phone.content).bills.map { it.note })
        assertArrayEquals(byteArrayOf(1, 2, 3), File(folder.root, "1731900000000-4f2a.jpg").readBytes())
        assertEquals(old, server.body)
        assertTrue(fresh in server.history.map { it.body })
        assertEquals(SyncOutcome.UpToDate, newApp.sync())
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
                override fun push(body: String, base: String, tag: String): PushResult = throw ServerFailure(0)
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

    @Test fun aPhoneRestoredFromAFileAsksRatherThanReplacingTheServer() = runBlocking {
        engine.sync()
        otherPhoneWrites("bills and the other phone's lunch")
        assertEquals(SyncOutcome.Downloaded, engine.sync())
        engine.replaceHere { wallet.content = "last month's backup" }
        assertConflict(engine.sync())
        assertConflict(engine.sync())
        assertEquals("bills and the other phone's lunch", server.body)
        assertEquals("last month's backup", wallet.content)
        assertEquals("the last sync time stays", 1000L, meta.meta.syncedAt)
    }

    @Test fun aRestoredPhoneReplacesTheServerWhenToldTo() = runBlocking {
        engine.sync()
        engine.replaceHere { wallet.content = "last month's backup" }
        assertConflict(engine.sync())
        assertEquals(SyncOutcome.Uploaded, engine.keepPhone())
        assertEquals("last month's backup", server.body)
        assertTrue("bills" in server.history.map { it.body })
        assertEquals(SyncOutcome.UpToDate, engine.sync())
    }

    @Test fun aPhoneWithEverythingDeletedCanTakeTheServersCopyBack() = runBlocking {
        engine.sync()
        engine.replaceHere { wallet.content = "a fresh wallet" }
        assertConflict(engine.sync())
        engine.keepServer()
        assertEquals("bills", wallet.content)
        assertEquals("bills", server.body)
        assertTrue("a fresh wallet" in server.history.map { it.body })
        assertEquals(SyncOutcome.UpToDate, engine.sync())
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
        assertTrue(ServerAddress.isValid("https://home.example.net/xrnwal"))
        assertFalse(ServerAddress.isValid("http://home.example.net/xrnwal"))
        assertFalse(ServerAddress.isValid("home.example.net"))
        assertFalse(ServerAddress.isValid("https://"))
        assertFalse(ServerAddress.isValid("http://10.0.2.2:5237"))
        assertTrue(ServerAddress.isValid("http://10.0.2.2:5237", emulator = true))
        assertFalse(ServerAddress.isValid("http://10.0.2.2.example.net", emulator = true))
        assertEquals("https://a.net/xrnwal", ServerAddress.clean(" https://a.net/xrnwal// "))
    }

    @Test fun anAddressSavedBeforeTheRenameMovesToTheNewPathAsTheAppOpens() {
        assertEquals("https://home.example.net/xrnwal", ServerAddress.renamed("https://home.example.net/xrnwallet"))
        for (kept in listOf(
            "https://home.example.net/xrnwal", "https://home.example.net/wallet",
            "https://home.example.net/xrnwallets", "https://xrnwallet.example.net",
        )) {
            assertEquals(kept, ServerAddress.renamed(kept))
        }
        val prefs = MemoryPrefs()
        prefs.edit().putString("address", "https://home.example.net/xrnwallet").putString("user", "alex")
            .putString("password", "sealed").putLong("version", 41).commit()
        assertEquals("https://home.example.net/xrnwal", SyncStore(prefs).address())
        assertEquals("https://home.example.net/xrnwal", prefs.getString("address", null))
        // The login and what this phone last matched stay as they were.
        assertEquals("alex", prefs.getString("user", null))
        assertEquals("sealed", prefs.getString("password", null))
        assertEquals(41L, SyncStore(prefs).load().version)
        assertEquals("https://home.example.net/xrnwal", prefs.getString("address", null))
        assertNull(SyncStore(MemoryPrefs()).address())
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

    @Test fun aWalletTooLargeForTheServerSaysSo() {
        // Past the server's 8 MB, every upload got 413 and the app said only "answered 413".
        assertEquals("Too large for the home server.", ServerFailure(413).message)
        assertFalse(ServerFailure(413).temporary)
    }
}
