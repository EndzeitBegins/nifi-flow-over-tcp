package io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.endzeitbegins.nifi.flowovertcp.internal.codec.send.toByteRepresentation
import java.nio.ByteOrder

private val objectMapper = ObjectMapper()

internal fun TestFlowFile.toByteRepresentation(): ByteArray {
    val attributeBytes = objectMapper.writeValueAsBytes(attributes)
    val contentBytes = content.toByteArray()

    val attributesLength: Int = attributeBytes.size
    val contentLength: Long = content.size.toLong()

    val attributesLengthBytes = attributesLength.toByteRepresentation(byteOrder = ByteOrder.BIG_ENDIAN)
    val contentLengthBytes = contentLength.toByteRepresentation(byteOrder = ByteOrder.BIG_ENDIAN)

    return attributesLengthBytes + contentLengthBytes + attributeBytes + contentBytes
}