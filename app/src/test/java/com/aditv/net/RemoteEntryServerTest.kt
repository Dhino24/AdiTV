package com.aditv.net

import java.net.InetAddress
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteEntryServerTest {
    private val http = OkHttpClient()
    private val server = RemoteEntryServer(RemoteEntryServer.Form.LOGIN, InetAddress.getLoopbackAddress())
    @After fun tearDown() = server.close()

    private fun ready() = server.start() as RemoteEntryServer.State.Ready
    private fun get(url: String) = http.newCall(Request.Builder().url(url).build()).execute().use { it.code to it.body.string() }
    private fun post(base: String, fields: Map<String, String>) = http.newCall(
        Request.Builder().url(base).post(FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()).build(),
    ).execute().use { it.code to it.body.string() }
    private fun token(url: String) = url.substringAfter("?t=")

    @Test fun qrLinkOpensFormAndValuesArriveOnce() {
        val r = ready()
        assertTrue("adresse locale, port aléatoire", r.url.startsWith("http://127.0.0.1:"))
        val (code, page) = get(r.url)
        assertEquals(200, code)
        assertTrue(page.contains("name=\"password\" type=\"password\""))
        assertFalse("aucun script dans la page", page.contains("<script"))

        val (c2, done) = post(r.shortUrl, mapOf("t" to token(r.url), "server" to "  http://exemple.test:8080 ", "username" to "moi", "password" to " p@ss w0rd "))
        assertEquals(200, c2)
        assertTrue(done.contains("C'est reçu"))
        val got = server.state.value as RemoteEntryServer.State.Received
        assertEquals("http://exemple.test:8080", got.values["server"])
        assertEquals("mot de passe intact (espaces conservés)", " p@ss w0rd ", got.values["password"])
        assertFalse("pas de valeur dans toString", got.toString().contains("w0rd"))

        // Usage unique : le serveur est fermé.
        assertTrue(runCatching { get(r.shortUrl) }.isFailure)
        server.consume()
        assertTrue(server.state.value is RemoteEntryServer.State.Closed)
    }

    @Test fun manualAddressNeedsThePin() {
        val r = ready()
        val (_, pinPage) = get(r.shortUrl)
        assertTrue(pinPage.contains("code à 4 chiffres"))
        assertFalse("le jeton n'est pas donné sans code", pinPage.contains(token(r.url)))
        val (code, form) = get(r.shortUrl + "?pin=" + r.pin)
        assertEquals(200, code)
        assertTrue("le formulaire porte le jeton", form.contains(token(r.url)))
    }

    @Test fun fiveWrongAttemptsCloseTheServer() {
        val r = ready()
        val wrong = ((r.pin.toInt() + 1) % 10_000).toString().padStart(4, '0')
        repeat(4) { assertEquals(403, get(r.shortUrl + "?pin=" + wrong).first) }
        // Un envoi sans bon jeton compte aussi.
        val (code, page) = post(r.shortUrl, mapOf("t" to "faux", "password" to "x"))
        assertEquals(403, code)
        assertTrue(page.contains("Trop d'essais"))
        val st = server.state.value
        assertTrue(st is RemoteEntryServer.State.Closed)
        assertTrue(runCatching { get(r.url) }.isFailure)
    }

    @Test fun oversizedOrUnknownRequestsAreRefused() {
        val r = ready()
        assertEquals(404, get(r.shortUrl + "autre").first)
        val big = "x".repeat(20_000)
        assertTrue(runCatching { post(r.shortUrl, mapOf("t" to token(r.url), "password" to big)) }.let { it.isFailure || it.getOrNull()?.first != 200 })
        assertTrue("toujours en attente", server.state.value is RemoteEntryServer.State.Ready)
    }

    @Test fun qrEncodesTheLink() {
        val m = com.aditv.ui.common.qrMatrix("http://192.168.1.20:43567/?t=" + "a".repeat(32))
        assertNotNull(m)
        assertTrue(m!!.width in 25..60)
    }
}
