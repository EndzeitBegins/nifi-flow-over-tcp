package io.github.endzeitbegins.nifi.flowovertcp.models

sealed interface ConnectionDestination {
    val parentProcessGroupId: String
    val destinationId: String

    data class Processor(
        override val parentProcessGroupId: String,
        override val destinationId: String,
    ): ConnectionDestination
}