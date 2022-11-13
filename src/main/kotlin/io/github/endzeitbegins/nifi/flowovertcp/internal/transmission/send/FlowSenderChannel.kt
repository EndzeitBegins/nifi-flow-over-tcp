package io.github.endzeitbegins.nifi.flowovertcp.internal.transmission.send

import io.github.endzeitbegins.nifi.flowovertcp.internal.attributes.Attributes
import org.apache.nifi.flowfile.FlowFile
import java.io.Closeable
import java.io.InputStream

/**
 * Use the library-agnostic [FlowSenderChannel]s to transmit [FlowFile]s over TCP connections.
 */
internal interface FlowSenderChannel {

    /**
     * Transmits both the [attributes] and [content] of size [contentLength] of a [FlowFile] over the channel.
     */
    fun sendFlow(
        attributes: Attributes,
        contentLength: Long,
        content: InputStream
    )
}