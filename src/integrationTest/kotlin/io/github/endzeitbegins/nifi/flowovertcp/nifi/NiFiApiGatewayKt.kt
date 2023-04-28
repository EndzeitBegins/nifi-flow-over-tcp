package io.github.endzeitbegins.nifi.flowovertcp.nifi

import io.github.endzeitbegins.nifi.flowovertcp.gateways.NiFiApiGateway
import io.github.endzeitbegins.nifi.flowovertcp.models.*
import io.github.endzeitbegins.nifi.flowovertcp.nifi.flow.NiFiTestFlow
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

internal fun <R> NiFiApiGateway.withTestFlow(block: (testFlow: NiFiTestFlow) -> R): R {
    val testFlow = setUpNiFiTestFlow()

    return block.invoke(testFlow)
}