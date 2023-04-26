package io.github.endzeitbegins.nifi.flowovertcp.testing.assertions

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import strikt.api.Assertion
import strikt.java.allBytes
import java.nio.file.Path

fun <T : Path> Assertion.Builder<T>.contentAsJson(): Assertion.Builder<Map<String, String?>> =
    allBytes().get("as JSON") {
        val contentString = this.decodeToString()

        Json.decodeFromString(contentString)
    }