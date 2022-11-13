package io.github.endzeitbegins.nifi.flowovertcp.internal.transmission.send

import org.apache.nifi.flowfile.FlowFile
import java.io.Closeable

/**
 * Use the library-agnostic [FlowSender] to manage [FlowSenderChannel]s,
 * which can be used to transmit [FlowFile]s over TCP connections.
 */
internal interface FlowSender<Channel : FlowSenderChannel> : Closeable {

    /**
     * Acquires a [channel][FlowSenderChannel] to transmit FlowFiles over.
     */
    fun acquireChannel(): Channel

    /**
     * [Acquires][acquireChannel] a [channel][FlowSenderChannel], executes the given [block] function on it, and [releases][releaseChannel] it.
     */
    fun <R> useChannel(block: (Channel) -> R): R {
        val channel = acquireChannel()

        return try {
            block.invoke(channel)
        } finally {
            releaseChannel(channel)
        }
    }

    /**
     * Releases any resources associated with the given [channel].
     */
    fun releaseChannel(channel: Channel)
}
