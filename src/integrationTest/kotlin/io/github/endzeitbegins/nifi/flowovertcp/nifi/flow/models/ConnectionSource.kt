package io.github.endzeitbegins.nifi.flowovertcp.nifi.flow.models

sealed interface ConnectionSource {
    val parentProcessGroupId: String
    val sourceId: String

    data class Processor(
        override val parentProcessGroupId: String,
        override val sourceId: String,
        val relationships: Set<String>,
    ): ConnectionSource
}