package io.github.endzeitbegins.nifi.flowovertcp.gateways

import io.github.endzeitbegins.nifi.flowovertcp.models.*

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

