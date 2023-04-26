package io.github.endzeitbegins.nifi.flowovertcp.internal.codec

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal fun Map<String, String?>.toJson(): String =
    Json.encodeToString(this)

internal fun String.parseJsonMap(): Map<String, String?> =
    Json.decodeFromString(this)
