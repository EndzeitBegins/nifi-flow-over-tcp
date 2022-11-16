package io.github.endzeitbegins.nifi.flowovertcp.internal.utils

import io.netty.handler.stream.ChunkedStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.util.*

internal fun Collection<InputStream>.concatenate(): InputStream {
    val enumeration = Vector(this).elements()

    return SequenceInputStream(enumeration)
}

internal fun InputStream.chunked() = ChunkedStream(this)