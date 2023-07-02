package io.github.endzeitbegins.nifi.flowovertcp.nifi.flow

import io.github.endzeitbegins.nifi.flowovertcp.nifi.flow.models.Connection
import io.github.endzeitbegins.nifi.flowovertcp.nifi.flow.models.ProcessGroup
import io.github.endzeitbegins.nifi.flowovertcp.nifi.flow.models.Processor

internal data class NiFiTestFlow(
    val rootProcessGroup: ProcessGroup,
    val processors: TestFlowProcessors,
    val connections: TestFlowConnections,
)

internal data class TestFlowProcessors(
    val listFiles: Processor,
    val fetchAttributesFile: Processor,
    val readContentJsonAsAttributes: Processor,
    val fetchContentFile: Processor,
    val computeHash: Processor,
    val transferFlowFile: Processor,
    val receiveFlowFile: Processor,
    val adjustFilenameForContentFile: Processor,
    val storeContent: Processor,
    val writeAttributesAsJsonInContent: Processor,
    val adjustFilenameForAttributesFile: Processor,
    val storeAttributes: Processor,
)

internal data class TestFlowConnections(
    val listFilesToFetchAttributesFile: Connection,
    val fetchAttributesFileToFetchAttributesFile: Connection,
    val fetchAttributesFileToReadContentJsonAsAttributes: Connection,
    val readContentJsonAsAttributesToReadContentJsonAsAttributes: Connection,
    val readContentJsonAsAttributesToFetchContentFile: Connection,
    val fetchContentFileToFetchContentFile: Connection,
    val fetchContentFileToComputeHash: Connection,
    val computeHashToTransferFlowFile: Connection,
    val transferFlowFileToTransferFlowFile: Connection,
    val receiveFlowFileToAdjustFilenameForContentFile: Connection,
    val adjustFilenameForContentFileToStoreContent: Connection,
    val storeContentToStoreContent: Connection,
    val storeContentToWriteAttributesAsJsonInContent: Connection,
    val writeAttributesAsJsonInContentToWriteAttributesAsJsonInContent: Connection,
    val writeAttributesAsJsonInContentToAdjustFilenameForAttributesFile: Connection,
    val adjustFilenameForAttributesFileToStoreAttributes: Connection,
    val storeAttributesToStoreAttributes: Connection,
)