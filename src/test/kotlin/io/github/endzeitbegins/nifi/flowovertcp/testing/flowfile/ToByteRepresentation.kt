package io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal fun Int.toByteRepresentation(byteOrder: ByteOrder): ByteArray =
    ByteBuffer.allocate(Int.SIZE_BYTES).order(byteOrder).putInt(this).array()

internal fun Long.toByteRepresentation(byteOrder: ByteOrder): ByteArray =
    ByteBuffer.allocate(Long.SIZE_BYTES).order(byteOrder).putLong(this).array()

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