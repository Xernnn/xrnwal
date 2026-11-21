package com.xern.xrnwal

import com.xern.xrnwal.data.Backup
import com.xern.xrnwal.data.BackupFile
import com.xern.xrnwal.data.BackupReader
import com.xern.xrnwal.data.Bill
import com.xern.xrnwal.data.BillType
import com.xern.xrnwal.data.Ledger
import com.xern.xrnwal.data.NotABackup
import com.xern.xrnwal.data.Settings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import kotlin.random.Random

class BackupFileTest {
    @get:Rule val folder = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Larger than a piece read or written at a time, and of a length that needs padding. */
    private val big = Random(7).nextBytes(200_001)
    private val small = byteArrayOf(1, 2, 3, 4)

    private fun backup(photos: Map<String, String> = emptyMap()) = Backup(
        settings = json.encodeToString(Settings(nickname = "Sam \"S\" \\ 🙂")),
        ledgers = listOf(Ledger(id = 1, name = "General")),
        accounts = emptyList(), categories = emptyList(), tags = emptyList(),
        bills = listOf(
            Bill(id = 1, ledgerId = 1, type = BillType.EXPENSE, amountCents = 1_895, currency = "AUD", atMillis = 1_000, note = "a {b} [c], \"d\"", photo = "/x/photos/receipt.jpg"),
            Bill(id = 2, ledgerId = 1, type = BillType.EXPENSE, amountCents = 650, currency = "AUD", atMillis = 2_000, photo = "/x/photos/tiny.jpg"),
        ),
        billTags = emptyList(), budgets = emptyList(), recurring = emptyList(), plans = emptyList(), deposits = emptyList(),
        checklists = emptyList(), checklistItems = emptyList(), reminders = emptyList(), rates = emptyList(), photos = photos,
    )

    private fun photo(name: String, bytes: ByteArray) = File(folder.newFolder(), name).apply { writeBytes(bytes) }

    @Test
    fun aBackupWrittenAPhotoAtATimeIsTheFileItAlwaysWas() {
        val out = ByteArrayOutputStream()
        BackupFile.write(out, backup(), listOf(photo("receipt.jpg", big), photo("tiny.jpg", small)))
        // As a whole-string reader reads it, as an app before this would.
        val read = json.decodeFromString<Backup>(out.toString(Charsets.UTF_8))
        assertEquals(backup().copy(photos = read.photos), read)
        assertEquals(mapOf("receipt.jpg" to big, "tiny.jpg" to small).mapValues { Base64.getEncoder().encodeToString(it.value) }, read.photos)
    }

    @Test
    fun aBackupIsReadAPhotoAtATimeWithItsPhotosInFiles() {
        val out = ByteArrayOutputStream()
        BackupFile.write(out, backup(), listOf(photo("receipt.jpg", big), photo("tiny.jpg", small)))
        val into = folder.newFolder()
        val rest = BackupFile.read(out.toByteArray().inputStream(), into)
        assertArrayEquals(big, File(into, "receipt.jpg").readBytes())
        assertArrayEquals(small, File(into, "tiny.jpg").readBytes())
        assertEquals(backup(), BackupReader.read(rest))
        assertTrue("photos" !in json.parseToJsonElement(rest).jsonObject)
    }

    @Test
    fun aFileFromBeforeReadsTheSame() {
        val encoder = Base64.getEncoder()
        // As the app wrote one whole, and as another writer might, with spaces and an escaped slash.
        val whole = json.encodeToString(backup(mapOf("receipt.jpg" to encoder.encodeToString(big), "../tiny.jpg" to encoder.encodeToString(small))))
        val spaced = whole.replace(",\"photos\":{", " ,\n \"photos\" : { ").replace("/", "\\/")
        for (text in listOf(whole, spaced)) {
            val into = folder.newFolder()
            val rest = BackupFile.read(text.byteInputStream(), into)
            assertArrayEquals(big, File(into, "receipt.jpg").readBytes())
            // Only the last part of a name is used.
            assertArrayEquals(small, File(into, "tiny.jpg").readBytes())
            assertEquals(backup(), BackupReader.read(rest))
        }
    }

    @Test
    fun aBackupFileXrnwalletMadeStillRestores() {
        // Written by xrnwallet 0.5.3, before the rename, from a made-up wallet.
        val into = folder.newFolder()
        val rest = javaClass.getResourceAsStream("/old-app/backup-0.5.3.json")!!.use { BackupFile.read(it, into) }
        val backup = BackupReader.read(rest)
        assertEquals(3, backup.version)
        assertEquals(listOf("Lunch", "Opal top-up", "Café shifts", "Phở", ""), backup.bills.map { it.note })
        assertEquals(listOf("Cash", "Everyday"), backup.accounts.map { it.name })
        assertEquals(listOf(85_400L), backup.adjustments.map { it.afterCents })
        assertEquals(listOf(1L to 40_000L), backup.budgets.filter { it.categoryId != null }.map { it.categoryId to it.amountCents })
        val settings = json.decodeFromString<Settings>(backup.settings)
        assertEquals("Alex", settings.nickname)
        assertEquals(false, settings.locked)
        // Its photo is named by the old app's folder. Restoring goes by the file name alone, which is here.
        val photo = backup.bills.mapNotNull { it.photo }.single()
        assertTrue(photo.startsWith("/data/user/0/com.xern.xrnwallet/files/photos/"))
        assertEquals(listOf("1731900000000-4f2a.jpg"), into.list()!!.toList())
        assertArrayEquals(ByteArray(3_000) { (it * 31 % 251).toByte() }, File(into, File(photo).name).readBytes())
    }

    @Test
    fun whatIsNotABackupIsRefused() {
        val whole = json.encodeToString(backup(mapOf("tiny.jpg" to Base64.getEncoder().encodeToString(small))))
        for (text in listOf("", "hello", "[1,2]", whole.dropLast(20), whole.replace("AQIDBA==", "AQ!DBA=="))) {
            val failure = runCatching { BackupReader.read(BackupFile.read(text.byteInputStream(), folder.newFolder())) }.exceptionOrNull()
            assertTrue("$text was taken", failure is NotABackup || failure is IllegalArgumentException)
        }
    }
}
