package io.github.endzeitbegins.nifi.flowovertcp.testing

import io.github.endzeitbegins.nifi.flowovertcp.testcontainers.NiFiContainerProvider
import io.github.endzeitbegins.nifi.flowovertcp.utils.sha256
import io.github.endzeitbegins.nifi.flowovertcp.utils.toJson
import kotlin.io.path.div
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.random.Random

internal data class FlowFileSeed(
    val fileNamePrefix: String,
    val fileSize: Int,
    val attributes: Map<String, String>,
)

internal fun FlowFileSeed.toFileSystemBasedFlowFile(
    contentSha256Hash: String,
) = FileSystemBasedFlowFile(
    baseFileName = fileNamePrefix,
    fileSize = fileSize.toLong(),
    fileSha256Hash = contentSha256Hash,
    attributes = attributes
)

internal fun List<FlowFileSeed>.provideToNiFiFlow(random: Random): List<FileSystemBasedFlowFile> =
    map { seed ->
        val targetDirectory = NiFiContainerProvider.mountedPathOnHost / "to-nifi"

        val contentTargetPath = targetDirectory / "${seed.fileNamePrefix}.content"
        val fileContent = random.nextBytes(seed.fileSize)
        contentTargetPath.writeBytes(fileContent)

        val attributesTargetPath = targetDirectory / "${seed.fileNamePrefix}.attributes"
        val attributesJson = seed.attributes.toJson()
        attributesTargetPath.writeText(attributesJson)

        seed.toFileSystemBasedFlowFile(fileContent.sha256)
    }