package io.github.endzeitbegins.nifi.flowovertcp.nifi.gateways

import io.github.endzeitbegins.nifi.flowovertcp.nifi.flow.models.*

interface NiFiApiGateway {

    fun createProcessGroup(
        parentProcessGroupId: String,
        name: String,
        position: Position = defaultPosition,
    ): ProcessGroup

    fun startProcessGroup(id: String)

    fun stopProcessGroup(id: String)

    fun createProcessor(
        parentProcessGroupId: String,
        type: String,
        name: String,
        properties: Map<String, String>,
        position: Position = defaultPosition,
        autoTerminatedRelationships: Set<String> = emptySet(),
    ): Processor

    fun startProcessor(id: String): Unit

    fun stopProcessor(id: String): Unit

    fun createConnection(
        parentProcessGroupId: String,
        source: ConnectionSource,
        destination: ConnectionDestination,
    ): Connection

    fun updateConnectionBackPressure(
        id: String,
        backPressureDataSizeThreshold: String? = null,
        backPressureObjectThreshold: Int? = null,
    )

    fun countFlowFilesInQueueOfConnection(id: String): Int
}

