package com.aditv.data.provider

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Les panneaux Xtream mélangent nombres, chaînes, null et "" : lecture tolérante. */
internal fun JsonObject.str(vararg keys: String): String? {
    for (k in keys) {
        val e = this[k] ?: continue
        if (e is JsonPrimitive && e !is JsonNull) {
            val c = e.content.trim()
            if (c.isNotEmpty() && c != "null") return c
        }
    }
    return null
}

internal fun JsonObject.long(vararg keys: String): Long? =
    str(*keys)?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }

internal fun JsonObject.int(vararg keys: String): Int? = long(*keys)?.toInt()

internal fun JsonObject.bool(vararg keys: String): Boolean? = when (str(*keys)?.lowercase()) {
    "1", "true", "yes" -> true
    "0", "false", "no" -> false
    else -> null
}

internal fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject
internal fun JsonElement.asArrayOrNull(): JsonArray? = this as? JsonArray
