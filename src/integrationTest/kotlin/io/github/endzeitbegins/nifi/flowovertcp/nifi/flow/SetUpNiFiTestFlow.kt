package io.github.endzeitbegins.nifi.flowovertcp.nifi.flow

import io.github.endzeitbegins.nifi.flowovertcp.nifi.createConnection
import io.github.endzeitbegins.nifi.flowovertcp.nifi.flow.models.Position
import io.github.endzeitbegins.nifi.flowovertcp.nifi.gateways.NiFiApiGateway
import io.github.endzeitbegins.nifi.flowovertcp.testcontainers.NiFiContainerProvider
import java.util.*

internal fun NiFiApiGateway.setUpNiFiTestFlow(socketPort: Int, startProcessGroup: Boolean): NiFiTestFlow {
    val parentProcessGroup = createProcessGroup(
        parentProcessGroupId = "root",
        name = "${UUID.randomUUID()}",
    )

    val processors = setUpTestProcessors(parentProcessGroupId = parentProcessGroup.id, socketPort = socketPort)
    val connections = setUpTestConnections(processors)

    if (startProcessGroup) {
        startProcessGroup(parentProcessGroup.id)
    }

    return NiFiTestFlow(
        rootProcessGroup = parentProcessGroup,
        processors = processors,
        connections = connections
    )
}

private fun NiFiApiGateway.setUpTestProcessors(parentProcessGroupId: String, socketPort: Int): TestFlowProcessors {
    val processorListFiles = createProcessor(
        parentProcessGroupId = parentProcessGroupId,
        type = "org.apache.nifi.processors.standard.ListFile",
        name = "listFiles",
        properties = mapOf(
            "Input Directory" to "${NiFiContainerProvider.mountedPathInContainer}/to-nifi/",
            "File Filter" to ".*[.]attributes\$"
        ),
        position = Position(x = 0, y = 0),
    )

    val processorFetchAttributesFile = createProcessor(
        parentProcessGroupId = parentProcessGroupId,
        type = "org.apache.nifi.processors.standard.FetchFile",
        name = "fetchAttributeFile",
        properties = mapOf(
            "Completion Strategy" to "Delete File"
        ),
        position = Position(x = 0, y = 200),
    )

    val processorReadContentJsonAsAttributes = createProcessor(
        parentProcessGroupId = parentProcessGroupId,
        type = "org.apache.nifi.processors.script.ExecuteScript",
        name = "parseJsonContentAsAttributes",
        properties = mapOf(
            "Script Engine" to "Groovy",
            "Script Body" to """
                import org.apache.commons.io.IOUtils
                import java.nio.charset.*
                
                def flowFile = session.get();
                if (flowFile == null) {
                    return;
                }
                def slurper = new groovy.json.JsonSlurper()
                def attrs = [:] as Map<String,String>
                
                session.read(flowFile,
                    { inputStream ->
                        def text = IOUtils.toString(inputStream, StandardCharsets.UTF_8)
                        def obj = slurper.parseText(text)
                        obj.each {k,v ->
                           attrs[k] = v.toString()
                        }
                    } as InputStreamCallback)
                    
                flowFile = session.putAllAttributes(flowFile, attrs)
                session.transfer(flowFile, REL_SUCCESS)
            """.trimIndent()
        ),
        position = Position(x = 0, y = 400),
    )

    val processorFetchContentFile = createProcessor(
        parentProcessGroupId = parentProcessGroupId,
        type = "org.apache.nifi.processors.standard.FetchFile",
        name = "fetchContentFile",
        properties = mapOf(
            "Completion Strategy" to "Delete File",
            "File to Fetch" to "\${absolute.path}/\${filename:substringBeforeLast('.attributes')}.content"
        ),
        position = Position(x = 0, y = 600),
    )

    val processorComputeHashOfContent = createProcessor(
        parentProcessGroupId = parentProcessGroupId,
        type = "org.apache.nifi.processors.standard.CryptographicHashContent",
        name = "computeHashOfContent",
        properties = emptyMap(),
        position = Position(x = 0, y = 800),
        autoTerminatedRelationships = setOf("failure")
    )

    val processorTransferFlowFile = createProcessor(
        parentProcessGroupId = parentProcessGroupId,
        type = "io.github.endzeitbegins.nifi.flowovertcp.PutFlowToTCP",
        name = "transferFlowFile",
        properties = mapOf(
            "Port" to "$socketPort"
        ),
        position = Position(x = 0, y = 1000),
        autoTerminatedRelationships = setOf("success")
    )

    val processorReceiveFlowFile = createProcessor(
        parentProcessGroupId = parentProcessGroupId,
        type = "io.github.endzeitbegins.nifi.flowovertcp.ListenFlowFromTCP",
        name = "receiveFlowFile",
        properties = mapOf(
            "Port" to "$socketPort"
        ),
        position = Position(x = 600, y = 0),
    )

    val processorAdjustFilenameForContentFile = createProcessor(
        parentProcessGroupId = parentProcessGroupId,
        type = "org.apache.nifi.processors.attributes.UpdateAttribute",
        name = "adjustFilenameForContentFile",
        properties = mapOf(
            "filename" to "\${filename:substringBeforeLast('.attributes')}.content"
        ),
        position = Position(x = 600, y = 200),
    )

    val processorStoreContent = createProcessor(
        parentProcessGroupId = parentProcessGroupId,
        type = "org.apache.nifi.processors.standard.PutFile",
        name = "storeContent",
        properties = mapOf(
            "Directory" to "${NiFiContainerProvider.mountedPathInContainer}/from-nifi/"
        ),
        position = Position(x = 600, y = 400),
    )

    val processorStoreAttributesAsJsonInContent = createProcessor(
        parentProcessGroupId = parentProcessGroupId,
        type = "org.apache.nifi.processors.standard.AttributesToJSON",
        name = "storeAttributesAsJsonInContent",
        properties = mapOf(
            "Destination" to "flowfile-content"
        ),
        position = Position(x = 600, y = 600),
    )

    val processorAdjustFilenameForAttributesFile = createProcessor(
        parentProcessGroupId = parentProcessGroupId,
        type = "org.apache.nifi.processors.attributes.UpdateAttribute",
        name = "adjustFilenameForAttributesFile",
        properties = mapOf(
            "filename" to "\${filename:substringBeforeLast('.content')}.attributes"
        ),
        position = Position(x = 600, y = 800),
    )

    val processorStoreAttributes = createProcessor(
        parentProcessGroupId = parentProcessGroupId,
        type = "org.apache.nifi.processors.standard.PutFile",
        name = "storeAttributes",
        properties = mapOf(
            "Directory" to "${NiFiContainerProvider.mountedPathInContainer}/from-nifi/"
        ),
        position = Position(x = 600, y = 1000),
        autoTerminatedRelationships = setOf("success")
    )

    return TestFlowProcessors(
        listFiles = processorListFiles,
        fetchAttributesFile = processorFetchAttributesFile,
        readContentJsonAsAttributes = processorReadContentJsonAsAttributes,
        fetchContentFile = processorFetchContentFile,
        computeHash = processorComputeHashOfContent,
        transferFlowFile = processorTransferFlowFile,
        receiveFlowFile = processorReceiveFlowFile,
        adjustFilenameForContentFile = processorAdjustFilenameForContentFile,
        storeContent = processorStoreContent,
        writeAttributesAsJsonInContent = processorStoreAttributesAsJsonInContent,
        adjustFilenameForAttributesFile = processorAdjustFilenameForAttributesFile,
        storeAttributes = processorStoreAttributes
    )
}

private fun NiFiApiGateway.setUpTestConnections(processors: TestFlowProcessors) = with(processors) {
    val connectionFromListFilesToFetchAttributesFile =
        createConnection(listFiles, fetchAttributesFile, setOf("success"))
    val connectionFromFetchAttributesFileToFetchAttributesFile =
        createConnection(fetchAttributesFile, fetchAttributesFile, setOf("not.found", "permission.denied", "failure"))
    val connectionFromFetchAttributesFileToReadContentJsonAsAttributes =
        createConnection(fetchAttributesFile, readContentJsonAsAttributes, setOf("success"))
    val connectionFromReadContentJsonAsAttributesToReadContentJsonAsAttributes =
        createConnection(readContentJsonAsAttributes, readContentJsonAsAttributes, setOf("failure"))
    val connectionFromReadContentJsonAsAttributesToFetchContentFile =
        createConnection(readContentJsonAsAttributes, fetchContentFile, setOf("success"))
    val connectionFromFetchContentFileToFetchContentFile =
        createConnection(fetchContentFile, fetchContentFile, setOf("not.found", "permission.denied", "failure"))
    val connectionFromFetchContentFileToComputeHash =
        createConnection(fetchContentFile, computeHash, setOf("success"))
    val connectionFromComputeHashToTransferFlowFile =
        createConnection(computeHash, transferFlowFile, setOf("success"))
    val connectionFromTransferFlowFileToTransferFlowFile =
        createConnection(transferFlowFile, transferFlowFile, setOf("failure"))
    val connectionFromReceiveFlowFileToAdjustFilenameForContentFile =
        createConnection(receiveFlowFile, adjustFilenameForContentFile, setOf("success"))
    val connectionFromAdjustFilenameForContentFileToStoreContent =
        createConnection(adjustFilenameForContentFile, storeContent, setOf("success"))
    val connectionFromStoreContentToStoreContent = createConnection(storeContent, storeContent, setOf("failure"))
    val connectionFromStoreContentToWriteAttributesAsJsonInContent =
        createConnection(storeContent, writeAttributesAsJsonInContent, setOf("success"))
    val connectionFromWriteAttributesAsJsonInContentToWriteAttributesAsJsonInContent =
        createConnection(writeAttributesAsJsonInContent, writeAttributesAsJsonInContent, setOf("failure"))
    val connectionFromWriteAttributesAsJsonInContentToAdjustFilenameForAttributesFile =
        createConnection(writeAttributesAsJsonInContent, adjustFilenameForAttributesFile, setOf("success"))
    val connectionFromAdjustFilenameForAttributesFileToStoreAttributes =
        createConnection(adjustFilenameForAttributesFile, storeAttributes, setOf("success"))
    val connectionFromStoreAttributesToStoreAttributes =
        createConnection(storeAttributes, storeAttributes, setOf("failure"))

    TestFlowConnections(
        listFilesToFetchAttributesFile = connectionFromListFilesToFetchAttributesFile,
        fetchAttributesFileToFetchAttributesFile = connectionFromFetchAttributesFileToFetchAttributesFile,
        fetchAttributesFileToReadContentJsonAsAttributes = connectionFromFetchAttributesFileToReadContentJsonAsAttributes,
        readContentJsonAsAttributesToReadContentJsonAsAttributes = connectionFromReadContentJsonAsAttributesToReadContentJsonAsAttributes,
        readContentJsonAsAttributesToFetchContentFile = connectionFromReadContentJsonAsAttributesToFetchContentFile,
        fetchContentFileToFetchContentFile = connectionFromFetchContentFileToFetchContentFile,
        fetchContentFileToComputeHash = connectionFromFetchContentFileToComputeHash,
        computeHashToTransferFlowFile = connectionFromComputeHashToTransferFlowFile,
        transferFlowFileToTransferFlowFile = connectionFromTransferFlowFileToTransferFlowFile,
        receiveFlowFileToAdjustFilenameForContentFile = connectionFromReceiveFlowFileToAdjustFilenameForContentFile,
        adjustFilenameForContentFileToStoreContent = connectionFromAdjustFilenameForContentFileToStoreContent,
        storeContentToStoreContent = connectionFromStoreContentToStoreContent,
        storeContentToWriteAttributesAsJsonInContent = connectionFromStoreContentToWriteAttributesAsJsonInContent,
        writeAttributesAsJsonInContentToWriteAttributesAsJsonInContent = connectionFromWriteAttributesAsJsonInContentToWriteAttributesAsJsonInContent,
        writeAttributesAsJsonInContentToAdjustFilenameForAttributesFile = connectionFromWriteAttributesAsJsonInContentToAdjustFilenameForAttributesFile,
        adjustFilenameForAttributesFileToStoreAttributes = connectionFromAdjustFilenameForAttributesFileToStoreAttributes,
        storeAttributesToStoreAttributes = connectionFromStoreAttributesToStoreAttributes,
    )
}
