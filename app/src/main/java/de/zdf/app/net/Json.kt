package de.zdf.app.net

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Kleine Helfer für tolerantes Lesen von JSON-Antworten. */
fun JsonElement?.obj(key: String): JsonObject? = (this as? JsonObject)?.get(key) as? JsonObject
fun JsonElement?.arr(key: String): JsonArray = ((this as? JsonObject)?.get(key) as? JsonArray) ?: JsonArray(emptyList())
fun JsonElement?.str(key: String): String? {
    val v = (this as? JsonObject)?.get(key) ?: return null
    if (v is JsonNull) return null
    return (v as? JsonPrimitive)?.content
}
fun JsonElement?.path(vararg keys: String): String? {
    var cur: JsonElement? = this
    for (k in keys.dropLast(1)) cur = cur.obj(k)
    return cur.str(keys.last())
}

/** "12.34" / "-12.34" → Cent */
fun parseCents(s: String?): Long? {
    if (s.isNullOrBlank()) return null
    return java.math.BigDecimal(s.trim()).movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
}
