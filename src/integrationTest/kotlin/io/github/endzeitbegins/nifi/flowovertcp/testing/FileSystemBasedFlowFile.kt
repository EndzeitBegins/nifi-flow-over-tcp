package io.github.endzeitbegins.nifi.flowovertcp.testing

import io.github.endzeitbegins.nifi.flowovertcp.testcontainers.NiFiContainerProvider
import io.github.endzeitbegins.nifi.flowovertcp.utils.destinationDirectory
import java.nio.file.Path
import kotlin.io.path.div

internal data class FileSystemBasedFlowFile(
    val baseFileName: String,
    val fileSize: Long,
    val fileSha256Hash: String,
    val attributes: Map<String, String>,
)

internal fun FileSystemBasedFlowFile.toContentFilePath(): Path =
    NiFiContainerProvider.destinationDirectory / "$baseFileName.content"

internal fun FileSystemBasedFlowFile.toAttributesFilePath(): Path =
    NiFiContainerProvider.destinationDirectory / "$baseFileName.attributes"