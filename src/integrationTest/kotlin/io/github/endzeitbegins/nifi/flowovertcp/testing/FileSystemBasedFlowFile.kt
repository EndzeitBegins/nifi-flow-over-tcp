package io.github.endzeitbegins.nifi.flowovertcp.testing

internal data class FlowFileSeed(
    val fileNamePrefix: String,
    val fileSize: Int,
    val attributes: Map<String, String>,
)

internal data class FileSystemBasedFlowFile(
    val baseFileName: String,
    val fileSize: Long,
    val fileSha256Hash: String,
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