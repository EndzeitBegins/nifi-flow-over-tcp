package io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.nifi.util.MockFlowFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal fun MockFlowFile.toTestFlowFile(): TestFlowFile = TestFlowFile(attributes, data.toList())
internal fun ByteArray.toTestFlowFile(): TestFlowFile {
    val attributesLengthBytes = take(4)
    val contentLengthBytes = drop(4).take(8)

    val attributesLength = attributesLengthBytes.toInt(byteOrder = ByteOrder.BIG_ENDIAN)
    val contentLength = contentLengthBytes.toLong(byteOrder = ByteOrder.BIG_ENDIAN)

    val attributeBytes = drop(12).take(attributesLength)
    val content = drop(12).drop(attributesLength)
    check(content.size.toLong() == contentLength) {
        "Content is ${content.size} bytes long, instead of expected size of $contentLength bytes."
    }

    val attributesJson = attributeBytes.toByteArray().decodeToString()
    val attributes: Map<String, String> = ObjectMapper().readValue(attributesJson)

    return TestFlowFile(attributes = attributes, content = content)
}

internal fun List<Byte>.toInt(byteOrder: ByteOrder) = ByteBuffer.wrap(this.toByteArray()).order(byteOrder).int
internal fun List<Byte>.toLong(byteOrder: ByteOrder) = ByteBuffer.wrap(this.toByteArray()).order(byteOrder).long
