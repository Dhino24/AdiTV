package com.aditv.data.provider

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Accès tolérants partagés (TMDB, fournisseurs). */
object JsonAccess {
    fun JsonObject.text(vararg keys: String): String? = str(*keys)
    fun JsonObject.integer(vararg keys: String): Int? = int(*keys)
    fun JsonObject.dbl(vararg keys: String): Double? = str(*keys)?.toDoubleOrNull()
    fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
}
