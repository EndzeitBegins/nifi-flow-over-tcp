package io.github.endzeitbegins.nifi.flowovertcp.internal.codec.send

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal fun Int.toByteRepresentation(byteOrder: ByteOrder): ByteArray =
    ByteBuffer.allocate(Int.SIZE_BYTES).order(byteOrder).putInt(this).array()

internal fun Long.toByteRepresentation(byteOrder: ByteOrder): ByteArray =
    ByteBuffer.allocate(Long.SIZE_BYTES).order(byteOrder).putLong(this).array()