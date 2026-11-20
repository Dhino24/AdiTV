package com.aditv.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform

/**
 * Première valeur tout de suite, puis au plus une toutes les [periodMs] (la plus récente).
 * Pendant une synchronisation, la base change à chaque paquet de titres : sans limite, chaque liste
 * serait reconstruite des dizaines de fois par minute (mémoire et processeur de la Stick).
 */
fun <T> Flow<T>.throttleLatest(periodMs: Long): Flow<T> = conflate().transform { emit(it); delay(periodMs) }

/** Dernière action à la télécommande : les tâches lourdes (guide TV) attendent que l'on ne navigue plus. */
object UserActivity {
    @Volatile var lastInputAt: Long = 0L
    fun touch() { lastInputAt = System.currentTimeMillis() }
    fun idleFor(ms: Long, now: Long = System.currentTimeMillis()) = now - lastInputAt >= ms
}
