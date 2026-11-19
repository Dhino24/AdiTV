package com.aditv.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RedactorTest {
    @After fun tearDown() = Redactor.clearSecrets()

    @Test fun masksQueryCredentials() {
        val out = Redactor.redact("http://h.example:8080/player_api.php?username=alice&password=s3cret&action=get_vod_streams")
        assertEquals("http://h.example:8080/player_api.php?username=***&password=***&action=get_vod_streams", out)
    }

    @Test fun masksXtreamStreamPath() {
        assertEquals("http://h/movie/***/***/123.mkv", Redactor.redact("http://h/movie/alice/s3cret/123.mkv"))
        assertEquals("http://h/live/***/***/9.ts", Redactor.redact("http://h/live/alice/s3cret/9.ts"))
    }

    @Test fun masksRegisteredSecretsAnywhere() {
        Redactor.registerSecret("Tr0ub4dor")
        val out = Redactor.redact("java.io.IOException: failed for Tr0ub4dor at host")
        assertFalse(out.contains("Tr0ub4dor"))
    }

    @Test fun masksUserInfo() {
        assertEquals("https://***@h.example/x", Redactor.redact("https://bob:pw@h.example/x"))
    }
}
