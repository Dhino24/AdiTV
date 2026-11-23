package com.aditv.data.live

import org.junit.Assert.assertTrue
import org.junit.Test

/** Gros abonnement (20 000 chaînes, surtout étrangères) : le classement doit rester rapide. */
class ChannelFamiliesPerfTest {
    @Test fun bigCatalogueIsFast() {
        val prefixes = listOf("FR", "UK", "US", "DE", "IT", "ES", "PT", "AR", "TR", "NL", "PL", "IN", "XX", "|EX|", "")
        val cats = listOf("FRANCE HD", "UK | SPORTS", "USA ENTERTAINMENT", "ARABIC NEWS", "TURKEY", "INDIA MOVIES", "AFRICA", "SENEGAL", "EX-YU", "LATINO")
        val names = (0 until 20_000).map { i -> "${prefixes[i % prefixes.size]}: Channel ${i % 997} HD" to cats[i % cats.size] }
        repeat(2) { names.take(500).forEach { (n, c) -> ChannelFamilies.place(n, c, null) } }
        val t = System.nanoTime()
        names.forEach { (n, c) -> ChannelFamilies.place(n, c, null); ChannelFamilies.isChampionsLeagueBroadcaster(n) }
        val ms = (System.nanoTime() - t) / 1_000_000
        println("PERF place x20000 = $ms ms")
        assertTrue("classement trop lent : $ms ms", ms < 1500)
    }
}
