package com.aditv.net

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneRemoteServerTest {
    private val received = mutableListOf<Pair<String, String?>>()
    private var closedReason: String? = null
    private val server = PhoneRemoteServer(InetAddress.getLoopbackAddress(), { k, t -> synchronized(received) { received += k to t } }, { closedReason = it })

    @After fun tearDown() = server.close()

    private fun call(path: String, method: String = "GET", body: String? = null): Pair<Int, String> {
        val c = URL(server.shortUrl!!.removeSuffix("/") + path).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.requestMethod = method
        if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); c.outputStream.use { it.write(body.toByteArray()) } }
        val code = c.responseCode
        val text = runCatching { (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty() }.getOrDefault("")
        return code to (c.getHeaderField("Location") ?: text)
    }

    private fun token() = server.url!!.substringAfter("t=")

    @Test fun pinRedirectsToTokenPage() {
        assertTrue(server.start())
        val (code, loc) = call("/?pin=${server.pin}")
        assertEquals(303, code); assertEquals("/?t=${token()}", loc)
        val (ok, html) = call("/?t=${token()}")
        assertEquals(200, ok); assertTrue(html.contains("TÉLÉCOMMANDE"))
    }

    @Test fun keysNeedTheTokenAndAreWhitelisted() {
        server.start()
        assertEquals(403, call("/k?t=bad&k=up", "POST").first)
        assertEquals(204, call("/k?t=${token()}&k=up", "POST").first)
        assertEquals(204, call("/k?t=${token()}&k=rm-rf", "POST").first)
        assertEquals(204, call("/s?t=${token()}", "POST", "q=Spider-Man").first)
        Thread.sleep(100)
        assertEquals(listOf("up" to null, "search" to "Spider-Man"), synchronized(received) { received.toList() })
    }

    @Test fun fiveWrongCodesCloseTheServer() {
        server.start()
        val wrong = if (server.pin == "0000") "1111" else "0000"
        repeat(5) { call("/?pin=$wrong") }
        assertTrue(server.closed)
        assertTrue(closedReason!!.contains("essais"))
    }
}
