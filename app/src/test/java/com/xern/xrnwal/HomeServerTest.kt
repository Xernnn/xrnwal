package com.xern.xrnwal

import com.xern.xrnwal.data.HomeServer
import com.xern.xrnwal.data.PushResult
import com.xern.xrnwal.data.ServerFailure
import com.xern.xrnwal.data.ServerLogin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.ServerSocket
import java.util.Collections
import java.util.zip.GZIPInputStream
import kotlin.concurrent.thread

/** A one-request-at-a-time HTTP server that keeps what each upload sent and answers from [answers]. */
private class UploadCatcher(private val answers: (Upload) -> String) {
    class Upload(val headers: Map<String, String>, val body: ByteArray) {
        val packed: Boolean get() = headers["content-encoding"] == "gzip"
        val text: String get() = String(if (packed) GZIPInputStream(body.inputStream()).readBytes() else body)
    }

    val uploads: MutableList<Upload> = Collections.synchronizedList(mutableListOf())
    private val socket = ServerSocket(0)
    val address = "http://127.0.0.1:${socket.localPort}"

    init {
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                client.use {
                    val input = it.getInputStream()
                    val head = generateSequence { line(input) }.takeWhile { line -> line.isNotEmpty() }.toList()
                    val headers = head.drop(1).associate { line ->
                        line.substringBefore(':').trim().lowercase() to line.substringAfter(':').trim()
                    }
                    val body = input.readNBytes(headers["content-length"]?.toInt() ?: 0)
                    val upload = Upload(headers, body)
                    uploads += upload
                    it.getOutputStream().write(answers(upload).toByteArray())
                }
            }
        }
    }

    private fun line(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (out.size() == 0) null else out.toString()
            if (b == '\n'.code) return out.toString().trimEnd('\r')
            out.write(b)
        }
    }

    fun close() = socket.close()
}

private fun answer(status: String, vararg headers: String) =
    "HTTP/1.1 $status\r\n" + headers.joinToString("") { "$it\r\n" } + "Content-Length: 0\r\nConnection: close\r\n\r\n"

class HomeServerTest {
    private val copy = """{"bills":[""" + (1..400).joinToString(",") { """{"id":$it,"note":"lunch"}""" } + "]}"
    private var server: UploadCatcher? = null

    private fun serve(answers: (UploadCatcher.Upload) -> String) =
        UploadCatcher(answers).also { server = it }

    private fun phone(at: UploadCatcher) = HomeServer(ServerLogin(at.address, "owner", "pw"), "test phone")

    @After
    fun close() {
        server?.close()
    }

    @Test
    fun `the copy goes up gzipped, a fraction of its size`() {
        // Sent whole, the phone's 160 KB copy was cut short by a dropped connection six times in two days.
        val at = serve { answer("200 OK", "ETag: \"1\"", "X-Tag: t1", "Accept-Encoding: gzip") }
        assertEquals(PushResult.Accepted(1, "t1"), phone(at).push(copy, "0", ""))
        val sent = at.uploads.single()
        assertTrue(sent.packed)
        assertEquals(copy, sent.text)
        assertTrue("${sent.body.size} of ${copy.length}", sent.body.size * 5 < copy.length)
    }

    @Test
    fun `a server from before gzip gets the copy again, plain`() {
        // It reads gzip as a broken document and answers 400, without saying it takes gzip.
        val at = serve { if (it.packed) answer("400 Bad Request") else answer("200 OK", "ETag: \"1\"") }
        assertEquals(PushResult.Accepted(1, ""), phone(at).push(copy, "0", ""))
        assertEquals(listOf(true, false), at.uploads.map { it.packed })
        assertEquals(copy, at.uploads.last().text)
    }

    @Test
    fun `a stale copy is told apart the same way, gzipped or not`() {
        val at = serve {
            answer(
                "412 Precondition Failed", "ETag: \"3\"", "X-Content-SHA256: abc", "X-Updated: then", "X-Tag: t3",
                "Accept-Encoding: gzip",
            )
        }
        assertEquals(PushResult.Stale(3, "abc", "then", "t3"), phone(at).push(copy, "2", "t2"))
        assertEquals(1, at.uploads.size)
    }

    @Test
    fun `a refusal from a server that takes gzip is not sent again`() {
        val at = serve { answer("400 Bad Request", "Accept-Encoding: gzip") }
        assertEquals(400, assertThrows(ServerFailure::class.java) { phone(at).push(copy, "0", "") }.status)
        assertEquals(1, at.uploads.size)
    }
}
