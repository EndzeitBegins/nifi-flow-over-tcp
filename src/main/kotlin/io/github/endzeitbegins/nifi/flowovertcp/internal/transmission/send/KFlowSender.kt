package io.github.endzeitbegins.nifi.flowovertcp.internal.transmission.send

import java.io.Closeable

internal interface KFlowChannel {}

internal interface KFlowSender<Channel: KFlowChannel> {

    fun acquireChannel(): Channel

    fun <R> useChannel(block: Channel.() -> R): R {
        val channel = acquireChannel()

        return try {
            block.invoke(channel)
        } finally {
            releaseChannel(channel)
        }
    }

    fun releaseChannel(channel: Channel)
}