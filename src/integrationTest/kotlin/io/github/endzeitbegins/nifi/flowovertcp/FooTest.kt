package io.github.endzeitbegins.nifi.flowovertcp

import io.github.endzeitbegins.nifi.flowovertcp.gateways.*
import io.github.endzeitbegins.nifi.flowovertcp.models.Processor
import io.github.endzeitbegins.nifi.flowovertcp.testcontainers.NiFiContainerProvider
import org.junit.jupiter.api.Test

class FooTest {

    private val niFiApiGateway: NiFiApiGateway = KtorNiFiApiGateway

    @Test
    internal fun `t o d o`() {
        NiFiContainerProvider.container

        val parentProcessGroupId = "root"

        val processorListFiles = niFiApiGateway.createProcessor(
            parentProcessGroupId = parentProcessGroupId,
            type = "org.apache.nifi.processors.standard.ListFile",
            name = "listFiles",
            properties = mapOf(
                "Input Directory" to "${NiFiContainerProvider.mountedPathInContainer}/to-nifi/",
            ),
            position = Position(x = 0, y = 0),
        )

        val processorFetchFile = niFiApiGateway.createProcessor(
            parentProcessGroupId = parentProcessGroupId,
            type = "org.apache.nifi.processors.standard.FetchFile",
            name = "fetchFile",
            properties = mapOf(
                "Completion Strategy" to "Delete File"
            ),
            position = Position(x = 0, y = 200),
            autoTerminatedRelationships = setOf(
                "not.found", "permission.denied", "failure"
            )
        )

        niFiApiGateway.createConnection(processorListFiles, processorFetchFile, setOf("success"))

        val processorComputeHashOfContent = niFiApiGateway.createProcessor(
            parentProcessGroupId = parentProcessGroupId,
            type = "org.apache.nifi.processors.standard.CryptographicHashContent",
            name = "computeHashOfContent",
            properties = emptyMap(),
            position = Position(x = 0, y = 400),
            autoTerminatedRelationships = setOf("failure")
        )

        niFiApiGateway.createConnection(processorFetchFile, processorComputeHashOfContent, setOf("success"))

        val processorTransferFlowFile = niFiApiGateway.createProcessor(
            parentProcessGroupId = parentProcessGroupId,
            type = "io.github.endzeitbegins.nifi.flowovertcp.PutFlowToTCP",
            name = "transferFlowFile",
            properties = mapOf(
                "Port" to "31337"
            ),
            position = Position(x = 0, y = 600),
            autoTerminatedRelationships = setOf("success")
        )

        niFiApiGateway.createConnection(processorComputeHashOfContent, processorTransferFlowFile, setOf("success"))
        niFiApiGateway.createConnection(processorTransferFlowFile, processorTransferFlowFile, setOf("failure"))


        val processorReceiveFlowFile = niFiApiGateway.createProcessor(
            parentProcessGroupId = parentProcessGroupId,
            type = "io.github.endzeitbegins.nifi.flowovertcp.ListenFlowFromTCP",
            name = "receiveFlowFile",
            properties = mapOf(
                "Port" to "31337"
            ),
            position = Position(x = 600, y = 0),
        )

        val processorStoreContent = niFiApiGateway.createProcessor(
            parentProcessGroupId = parentProcessGroupId,
            type = "org.apache.nifi.processors.standard.PutFile",
            name = "storeContent",
            properties = mapOf(
                "Directory" to "${NiFiContainerProvider.mountedPathInContainer}/from-nifi/"
            ),
            position = Position(x = 600, y = 200),
        )

        niFiApiGateway.createConnection(processorReceiveFlowFile, processorStoreContent, setOf("success"))
        niFiApiGateway.createConnection(processorStoreContent, processorStoreContent, setOf("failure"))

        val processorStoreAttributesAsJsonInContent = niFiApiGateway.createProcessor(
            parentProcessGroupId = parentProcessGroupId,
            type = "org.apache.nifi.processors.standard.AttributesToJSON",
            name = "storeAttributesAsJsonInContent",
            properties = mapOf(
                "Destination" to "flowfile-content"
            ),
            position = Position(x = 600, y = 400),
        )

        niFiApiGateway.createConnection(
            processorStoreContent,
            processorStoreAttributesAsJsonInContent,
            setOf("success")
        )
        niFiApiGateway.createConnection(
            processorStoreAttributesAsJsonInContent,
            processorStoreAttributesAsJsonInContent,
            setOf("failure")
        )

        val processorAdjustFilenameForAttributesFile = niFiApiGateway.createProcessor(
            parentProcessGroupId = parentProcessGroupId,
            type = "org.apache.nifi.processors.attributes.UpdateAttribute",
            name = "adjustFilenameForAttributesFile",
            properties = mapOf(
                "filename" to "\${filename}.attributes"
            ),
            position = Position(x = 600, y = 600),
        )

        niFiApiGateway.createConnection(
            processorStoreAttributesAsJsonInContent,
            processorAdjustFilenameForAttributesFile,
            setOf("success")
        )


        val processorStoreAttributes = niFiApiGateway.createProcessor(
            parentProcessGroupId = parentProcessGroupId,
            type = "org.apache.nifi.processors.standard.PutFile",
            name = "storeAttributes",
            properties = mapOf(
                "Directory" to "${NiFiContainerProvider.mountedPathInContainer}/from-nifi/"
            ),
            position = Position(x = 600, y = 800),
            autoTerminatedRelationships = setOf("success")
        )

        niFiApiGateway.createConnection(
            processorAdjustFilenameForAttributesFile,
            processorStoreAttributes,
            setOf("success")
        )
        niFiApiGateway.createConnection(processorStoreAttributes, processorStoreAttributes, setOf("failure"))


        niFiApiGateway.startProcessGroup("root")

// ListFile (*.attributes); FetchFile (x.attributes); FetchFile (x.content);

        TODO("Not yet implemented")
    }
}

private fun NiFiApiGateway.createConnection(
    source: Processor,
    destination: Processor,
    relationships: Set<String>,
): Connection =
    createConnection(
        parentProcessGroupId = source.parentProcessGroupId,
        source = ConnectionSource.Processor(source.parentProcessGroupId, source.id, relationships),
        destination = ConnectionDestination.Processor(destination.parentProcessGroupId, destination.id),
    )