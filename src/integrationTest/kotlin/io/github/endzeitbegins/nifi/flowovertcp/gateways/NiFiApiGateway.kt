package io.github.endzeitbegins.nifi.flowovertcp.gateways

import io.github.endzeitbegins.nifi.flowovertcp.models.Processor

interface NiFiApiGateway {

    fun createProcessor(
        parentProcessGroupId: String,
        type: String,
        name: String,
        properties: Map<String, String>,
        position: Position = defaultPosition,
        autoTerminatedRelationships: Set<String> = emptySet()
    ): Processor

    fun createConnection(
        parentProcessGroupId: String,
        source: ConnectionSource,
        destination: ConnectionDestination,
    ): Connection

    fun startProcessGroup(id: String)
}

object Connection

sealed interface ConnectionSource {
    val parentProcessGroupId: String
    val sourceId: String

    data class Processor(
        override val parentProcessGroupId: String,
        override val sourceId: String,
        val relationships: Set<String>,
    ): ConnectionSource
}

sealed interface ConnectionDestination {
    val parentProcessGroupId: String
    val destinationId: String

    data class Processor(
        override val parentProcessGroupId: String,
        override val destinationId: String,
    ): ConnectionDestination
}

val defaultPosition = Position(x = 0, y = 0)
data class Position(val x: Int, val y: Int)
