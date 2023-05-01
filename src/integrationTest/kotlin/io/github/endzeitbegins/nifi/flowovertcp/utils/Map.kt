package io.github.endzeitbegins.nifi.flowovertcp.utils

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal fun Map<String, String>.toJson() =
    Json.encodeToString(this)