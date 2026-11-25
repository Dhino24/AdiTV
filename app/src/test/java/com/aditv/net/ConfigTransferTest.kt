package com.aditv.net

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ConfigTransferTest {
    @Test fun roundTrip() {
        val p = ConfigTransfer.Payload("http://srv.example:8080", "user", "p@ss&=word", "tmdbkey", null, "fdkey")
        assertEquals(p, ConfigTransfer.decode(ConfigTransfer.encode(p)))
        assertNull(ConfigTransfer.decode("n'importe quoi"))
    }

    /** Réseau local uniquement : jamais une adresse Internet ni un nom d'hôte. */
    @Test fun onlyLocalNetworkTargets() {
        assertNotNull(ConfigTransfer.target("192.168.1.20:41234"))
        assertNotNull(ConfigTransfer.target("http://10.0.0.5:5000/"))
        assertNull(ConfigTransfer.target("8.8.8.8:80"))
        assertNull(ConfigTransfer.target("exemple.com:8080"))
        assertNull(ConfigTransfer.target("pas une adresse"))
    }
}
