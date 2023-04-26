package io.github.endzeitbegins.nifi.flowovertcp.gateways

import io.github.endzeitbegins.nifi.flowovertcp.models.*
import io.github.endzeitbegins.nifi.flowovertcp.testcontainers.NiFiContainerProvider

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

internal fun NiFiApiGateway.setUpNiFiFlow() {
    val parentProcessGroupId = "root"

    val processorListFiles = createProcessor(
        parentProcessGroupId = parentProcessGroupId,
        type = "org.apache.nifi.processors.standard.ListFile",
        name = "listFiles",
        properties = mapOf(
            "Input Directory" to "${NiFiContainerProvider.mountedPathInContainer}/to-nifi/",
        ),
        position = Position(x = 0, y = 0),
    )

    val processorFetchFile = createProcessor(
        parentProcessGroupId = parentProcessGroupId,
        type = "org.apache.nifi.processors.standard.FetchFile",
        name = "fetchFile",
        properties = mapOf(
            "Completion Strategy" to "Delete File"
        ),
        position = Position(x = 0, y = 200),
    )

    createConnection(processorListFiles, processorFetchFile, setOf("success"))
    createConnection(processorFetchFile, processorFetchFile, setOf("not.found", "permission.denied", "failure"))

    val processorComputeHashOfContent = createProcessor(
        parentProcessGroupId = parentProcessGroupId,
        type = "org.apache.nifi.processors.standard.CryptographicHashContent",
        name = "computeHashOfContent",
        properties = emptyMap(),
        position = Position(x = 0, y = 400),
        autoTerminatedRelationships = setOf("failure")
    )

    createConnection(processorFetchFile, processorComputeHashOfContent, setOf("success"))

    val processorTransferFlowFile = createProcessor(
        parentProcessGroupId = parentProcessGroupId,
        type = "io.github.endzeitbegins.nifi.flowovertcp.PutFlowToTCP",
        name = "transferFlowFile",
        properties = mapOf(
            "Port" to "31337"
        ),
        position = Position(x = 0, y = 600),
        autoTerminatedRelationships = setOf("success")
    )

    createConnection(processorComputeHashOfContent, processorTransferFlowFile, setOf("success"))
    createConnection(processorTransferFlowFile, processorTransferFlowFile, setOf("failure"))


    val processorReceiveFlowFile = createProcessor(
        parentProcessGroupId = parentProcessGroupId,
        type = "io.github.endzeitbegins.nifi.flowovertcp.ListenFlowFromTCP",
        name = "receiveFlowFile",
        properties = mapOf(
            "Port" to "31337"
        ),
        position = Position(x = 600, y = 0),
    )

    val processorStoreContent = createProcessor(
        parentProcessGroupId = parentProcessGroupId,
        type = "org.apache.nifi.processors.standard.PutFile",
        name = "storeContent",
        properties = mapOf(
            "Directory" to "${NiFiContainerProvider.mountedPathInContainer}/from-nifi/"
        ),
        position = Position(x = 600, y = 200),
    )

    createConnection(processorReceiveFlowFile, processorStoreContent, setOf("success"))
    createConnection(processorStoreContent, processorStoreContent, setOf("failure"))

    val processorStoreAttributesAsJsonInContent = createProcessor(
        parentProcessGroupId = parentProcessGroupId,
        type = "org.apache.nifi.processors.standard.AttributesToJSON",
        name = "storeAttributesAsJsonInContent",
        properties = mapOf(
            "Destination" to "flowfile-content"
        ),
        position = Position(x = 600, y = 400),
    )

    createConnection(
        processorStoreContent,
        processorStoreAttributesAsJsonInContent,
        setOf("success")
    )
    createConnection(
        processorStoreAttributesAsJsonInContent,
        processorStoreAttributesAsJsonInContent,
        setOf("failure")
    )

    val processorAdjustFilenameForAttributesFile = createProcessor(
        parentProcessGroupId = parentProcessGroupId,
        type = "org.apache.nifi.processors.attributes.UpdateAttribute",
        name = "adjustFilenameForAttributesFile",
        properties = mapOf(
            "filename" to "\${filename}.attributes"
        ),
        position = Position(x = 600, y = 600),
    )

    createConnection(
        processorStoreAttributesAsJsonInContent,
        processorAdjustFilenameForAttributesFile,
        setOf("success")
    )


    val processorStoreAttributes = createProcessor(
        parentProcessGroupId = parentProcessGroupId,
        type = "org.apache.nifi.processors.standard.PutFile",
        name = "storeAttributes",
        properties = mapOf(
            "Directory" to "${NiFiContainerProvider.mountedPathInContainer}/from-nifi/"
        ),
        position = Position(x = 600, y = 800),
        autoTerminatedRelationships = setOf("success")
    )

    createConnection(
        processorAdjustFilenameForAttributesFile,
        processorStoreAttributes,
        setOf("success")
    )
    createConnection(processorStoreAttributes, processorStoreAttributes, setOf("failure"))

    startProcessGroup(parentProcessGroupId)
}
