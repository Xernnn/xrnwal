package com.xern.xrnwal.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.util.Base64

/** A file that isn't a backup this app wrote, or is cut short. */
class NotABackup(message: String) : Exception(message)

/**
 * A backup file, written and read a piece at a time so its photos are never in memory together: the
 * JSON a [Backup] encodes to, with each photo's base64 written from its file as it goes, and on reading
 * decoded into a file as it comes. Files are the same as before it streamed, so either reads the other's.
 */
object BackupFile {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Bytes of a photo encoded at a time. A multiple of three, so no piece but the last is padded. */
    private const val BYTES = 48 * 1024

    /** Characters of base64 decoded at a time. A multiple of four, for the same reason. */
    private const val CHARS = 64 * 1024

    /** Writes [backup], with [photos] as its photos by file name in place of any it holds. */
    fun write(out: OutputStream, backup: Backup, photos: List<File>) {
        val writer = out.bufferedWriter()
        val fields = JsonObject(json.encodeToJsonElement(backup).jsonObject - "photos")
        writer.write(fields.toString().dropLast(1))
        writer.write(if (fields.isEmpty()) "\"photos\":{" else ",\"photos\":{")
        val encoder = Base64.getEncoder()
        val buffer = ByteArray(BYTES)
        photos.distinctBy { it.name }.forEachIndexed { index, photo ->
            if (index > 0) writer.write(",")
            writer.write(JsonPrimitive(photo.name).toString())
            writer.write(":\"")
            photo.inputStream().use { input ->
                while (true) {
                    val read = fill(input, buffer)
                    if (read > 0) writer.write(encoder.encodeToString(if (read == buffer.size) buffer else buffer.copyOf(read)))
                    if (read < buffer.size) break
                }
            }
            writer.write("\"")
        }
        writer.write("}}")
        writer.flush()
    }

    /** Reads into [buffer] until it is full or [input] ends, and says how much it read. */
    private fun fill(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = input.read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    /**
     * Reads a backup file from [input], decoding each photo into a file of [photoDir] under the last part
     * of its name, and returns the rest of the file, without photos, for [BackupReader.read].
     */
    fun read(input: InputStream, photoDir: File): String {
        val scanner = Scanner(input.bufferedReader())
        val rest = StringBuilder("{")
        scanner.expect('{')
        var first = true
        if (scanner.peekPast() == '}'.code) return "{}".also { scanner.next() }
        while (true) {
            val key = scanner.rawString()
            scanner.expect(':')
            if (key == "\"photos\"") {
                readPhotos(scanner, photoDir)
            } else {
                if (!first) rest.append(',')
                rest.append(key).append(':')
                scanner.copyValue(rest)
                first = false
            }
            when (scanner.nextPast()) {
                ','.code -> continue
                '}'.code -> break
                else -> throw NotABackup("not a backup")
            }
        }
        return rest.append('}').toString()
    }

    private fun readPhotos(scanner: Scanner, photoDir: File) {
        scanner.expect('{')
        if (scanner.peekPast() == '}'.code) {
            scanner.next()
            return
        }
        val decoder = Base64.getDecoder()
        val chars = CharArray(CHARS)
        while (true) {
            val name = json.decodeFromString<String>(scanner.rawString())
            scanner.expect(':')
            scanner.expect('"')
            // Names come from the file, so only the last part of each is ever used as a path.
            File(photoDir, File(name).name).outputStream().buffered().use { out ->
                var count = 0
                while (true) {
                    var c = scanner.next()
                    if (c == '"'.code) break
                    // Base64 needs no escape, but a writer may still put one before a slash.
                    if (c == '\\'.code) c = scanner.next().takeIf { it == '/'.code } ?: throw NotABackup("a photo is not base64")
                    if (c < 0) throw NotABackup("the file ends early")
                    chars[count++] = c.toChar()
                    if (count == chars.size) {
                        out.write(decoder.decode(String(chars, 0, count)))
                        count = 0
                    }
                }
                if (count > 0) out.write(decoder.decode(String(chars, 0, count)))
            }
            when (scanner.nextPast()) {
                ','.code -> continue
                '}'.code -> return
                else -> throw NotABackup("not a backup")
            }
        }
    }

    /** The few steps of JSON reading the file's outer object needs, one character at a time. */
    private class Scanner(private val reader: Reader) {
        private var peeked = NONE

        fun peek(): Int {
            if (peeked == NONE) peeked = reader.read()
            return peeked
        }

        fun next(): Int = peek().also { peeked = NONE }

        private fun skipSpace() {
            while (peek().let { it == ' '.code || it == '\n'.code || it == '\r'.code || it == '\t'.code }) next()
        }

        fun peekPast(): Int = skipSpace().let { peek() }

        fun nextPast(): Int = skipSpace().let { next() }

        fun expect(c: Char) {
            if (nextPast() != c.code) throw NotABackup("not a backup")
        }

        /** A string as written, quotes and escapes included. */
        fun rawString(): String = StringBuilder().also {
            if (peekPast() != '"'.code) throw NotABackup("not a backup")
            copyString(it)
        }.toString()

        private fun copyString(to: StringBuilder) {
            to.append(next().toChar())
            while (true) {
                val c = next()
                if (c < 0) throw NotABackup("the file ends early")
                to.append(c.toChar())
                if (c == '\\'.code) to.append(next().takeIf { it >= 0 }?.toChar() ?: throw NotABackup("the file ends early"))
                else if (c == '"'.code) return
            }
        }

        /** One value, of any kind, copied as written. */
        fun copyValue(to: StringBuilder) {
            when (peekPast()) {
                '"'.code -> copyString(to)
                '{'.code, '['.code -> {
                    var depth = 0
                    do {
                        when (val c = peek()) {
                            '"'.code -> copyString(to)
                            -1 -> throw NotABackup("the file ends early")
                            else -> {
                                next()
                                to.append(c.toChar())
                                if (c == '{'.code || c == '['.code) depth++
                                if (c == '}'.code || c == ']'.code) depth--
                            }
                        }
                    } while (depth > 0)
                }
                else -> while (peek().let { it >= 0 && it != ','.code && it != '}'.code && it != ']'.code && !Character.isWhitespace(it) }) {
                    to.append(next().toChar())
                }
            }
        }

        private companion object {
            const val NONE = -2
        }
    }
}
