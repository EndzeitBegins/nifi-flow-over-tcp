package io.github.endzeitbegins.nifi.flowovertcp.nifi.flow

import io.github.endzeitbegins.nifi.flowovertcp.models.ProcessGroup
import io.github.endzeitbegins.nifi.flowovertcp.models.Processor

internal data class NiFiTestFlow(
    val rootProcessGroup: ProcessGroup,
    val processors: TestProcessors,
)

internal data class TestProcessors(
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