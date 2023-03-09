package io.github.endzeitbegins.nifi.flowovertcp

import io.github.endzeitbegins.nifi.flowovertcp.gateways.ConnectionDestination
import io.github.endzeitbegins.nifi.flowovertcp.gateways.ConnectionSource
import io.github.endzeitbegins.nifi.flowovertcp.gateways.KtorNiFiApiGateway
import io.github.endzeitbegins.nifi.flowovertcp.testcontainers.NiFiContainerProvider
import org.junit.jupiter.api.Test

class FooTest {

    private val niFiApiGateway = KtorNiFiApiGateway

    @Test
    internal fun `t o d o`() {
        NiFiContainerProvider.container

        val parentProcessGroupId = "root"

        val processorListFiles = niFiApiGateway.createProcessor(
            parentProcessGroupId = parentProcessGroupId,
            type = "org.apache.nifi.processors.standard.ListFile",
            name = "listFiles",
            properties = mapOf(
                "Input Directory" to "/foo/bar", // TODO change me
            )
        )

        val processorFetchFile = niFiApiGateway.createProcessor(
            parentProcessGroupId = parentProcessGroupId,
            type = "org.apache.nifi.processors.standard.FetchFile",
            name = "fetchFile",
            properties = emptyMap(),
            autoTerminatedRelationships = setOf(
                "not.found", "permission.denied", "failure"
            )
        )

        niFiApiGateway.createConnection(
            parentProcessGroupId,
            ConnectionSource.Processor(parentProcessGroupId, processorListFiles.id, setOf("success")),
            ConnectionDestination.Processor(parentProcessGroupId, processorFetchFile.id),
        )

        val processorComputeHashOfContent = niFiApiGateway.createProcessor(
            parentProcessGroupId = parentProcessGroupId,
            type = "org.apache.nifi.processors.standard.CryptographicHashContent",
            name = "computeHashOfContent",
            properties = emptyMap(),
            autoTerminatedRelationships = setOf("failure")
        )

        niFiApiGateway.createConnection(
            parentProcessGroupId,
            ConnectionSource.Processor(parentProcessGroupId, processorFetchFile.id, setOf("success")),
            ConnectionDestination.Processor(parentProcessGroupId, processorComputeHashOfContent.id),
        )

        val processorTransferFlowFile = niFiApiGateway.createProcessor(
            parentProcessGroupId = parentProcessGroupId,
            type = "io.github.endzeitbegins.nifi.flowovertcp.PutFlowToTCP",
            name = "transferFlowFile",
            properties = mapOf(
                "Port" to "31337"
            ),
            autoTerminatedRelationships = setOf("success")
        )

        niFiApiGateway.createConnection(
            parentProcessGroupId,
            ConnectionSource.Processor(parentProcessGroupId, processorComputeHashOfContent.id, setOf("success")),
            ConnectionDestination.Processor(parentProcessGroupId, processorTransferFlowFile.id),
        )



        val processorReceiveFlowFile = niFiApiGateway.createProcessor(
            parentProcessGroupId = parentProcessGroupId,
            type = "io.github.endzeitbegins.nifi.flowovertcp.ListenFlowFromTCP",
            name = "receiveFlowFile",
            properties = mapOf(
                "Port" to "31337"
            ),
        )

// ListFile (*.attributes); FetchFile (x.attributes); FetchFile (x.content);

        TODO("Not yet implemented")
    }
}

