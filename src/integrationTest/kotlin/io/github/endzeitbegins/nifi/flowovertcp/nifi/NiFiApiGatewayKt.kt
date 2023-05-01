package io.github.endzeitbegins.nifi.flowovertcp.nifi

import io.github.endzeitbegins.nifi.flowovertcp.nifi.gateways.NiFiApiGateway
import io.github.endzeitbegins.nifi.flowovertcp.nifi.flow.NiFiTestFlow
import io.github.endzeitbegins.nifi.flowovertcp.nifi.flow.models.Connection
import io.github.endzeitbegins.nifi.flowovertcp.nifi.flow.models.ConnectionDestination
import io.github.endzeitbegins.nifi.flowovertcp.nifi.flow.models.ConnectionSource
import io.github.endzeitbegins.nifi.flowovertcp.nifi.flow.models.Processor
import io.github.endzeitbegins.nifi.flowovertcp.nifi.flow.setUpNiFiTestFlow

internal fun NiFiApiGateway.createConnection(
    source: Processor,
    destination: Processor,
    relationships: Set<String>,
): Connection =
    createConnection(
        parentProcessGroupId = source.parentProcessGroupId,
        source = ConnectionSource.Processor(source.parentProcessGroupId, source.id, relationships),
        destination = ConnectionDestination.Processor(destination.parentProcessGroupId, destination.id),
    )

private var socketPort = 31337

internal fun <R> NiFiApiGateway.withTestFlow(block: (testFlow: NiFiTestFlow) -> R): R {
    val testFlow = setUpNiFiTestFlow(socketPort)
    socketPort += 1

    return block.invoke(testFlow)
}