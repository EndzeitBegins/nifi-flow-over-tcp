package io.github.endzeitbegins.nifi.flowovertcp.testing.assertions

import io.github.endzeitbegins.nifi.flowovertcp.testing.FileSystemBasedFlowFile
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import strikt.api.Assertion
import strikt.assertions.hasEntry
import strikt.assertions.isEqualTo
import strikt.java.allBytes
import strikt.java.exists
import strikt.java.size
import java.nio.file.Files
import java.nio.file.Path

internal fun <T : Path> Assertion.Builder<T>.containsAttributesFrom(flowFile: FileSystemBasedFlowFile): Assertion.Builder<T> =
    exists().withContentAsJson {
        for (attribute in flowFile.attributes) {
            hasEntry(attribute.key, attribute.value)
        }
        hasEntry("content_SHA-256", flowFile.fileSha256Hash)
    }

internal fun <T : Path> Assertion.Builder<T>.matchesContentFrom(flowFile: FileSystemBasedFlowFile): Assertion.Builder<T> =
    exists()
        .and {
            size.isEqualTo(flowFile.fileSize)
            allBytes().sha256HashSum.isEqualTo(flowFile.fileSha256Hash)
        }

fun <T : Path> Assertion.Builder<T>.withContentAsJson(
    block: Assertion.Builder<Map<String, String?>>.() -> Unit,
): Assertion.Builder<T> = with(
    description = "content as JSON",
    function = {
        val bytes = Files.readAllBytes(this)

        Json.decodeFromString(
            bytes.decodeToString()
        )
    },
    block = block
)