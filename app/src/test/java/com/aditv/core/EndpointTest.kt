package com.aditv.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointTest {
    private fun ok(s: String) = Endpoint.canonicalize(s) as Endpoint.Result.Ok

    @Test fun assumesHttpsWithoutScheme() {
        val r = ok("Provider.Example:8080")
        assertEquals("https://provider.example:8080", Endpoint.display(r.url))
        assertTrue(r.schemeWasAssumed)
    }

    @Test fun keepsExplicitHttpAndStripsScriptAndQuery() {
        val r = ok("http://provider.example:25461/player_api.php?username=a&password=b")
        assertEquals("http://provider.example:25461", Endpoint.display(r.url))
    }

    @Test fun keepsSubPath() {
        assertEquals("https://p.example/iptv", Endpoint.display(ok("https://p.example/iptv/").url))
    }

    @Test fun rejectsCredentialsInUrlAndOtherSchemes() {
        assertTrue(Endpoint.canonicalize("http://u:p@p.example") is Endpoint.Result.Invalid)
        assertTrue(Endpoint.canonicalize("rtmp://p.example") is Endpoint.Result.Invalid)
        assertTrue(Endpoint.canonicalize("  ") is Endpoint.Result.Invalid)
    }
}
