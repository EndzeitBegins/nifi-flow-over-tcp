package io.github.endzeitbegins.nifi.flowovertcp.internal.transmission.netty.send

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.endzeitbegins.nifi.flowovertcp.internal.attributes.Attributes
import io.github.endzeitbegins.nifi.flowovertcp.internal.transmission.send.FlowSenderChannel
import io.netty.channel.Channel
import net.nerdfunk.nifi.flow.transport.message.FlowMessage
import java.io.InputStream

private val objectMapper = ObjectMapper()

internal class NettyFlowSenderChannel(internal val nettyChannel: Channel) : FlowSenderChannel {

    override fun sendFlow(attributes: Attributes, contentLength: Long, content: InputStream) {
        val attributesAsBytes = objectMapper.writeValueAsBytes(attributes)

        val headerLength = attributesAsBytes.size

        val headerAndAttributes = FlowMessage().apply {
            this.headerlength = headerLength
            this.payloadlength = contentLength
            this.header = attributesAsBytes
        }

        nettyChannel.transmit(headerAndAttributes)
        nettyChannel.transmit(content)
    }
}

private fun Channel.transmit(msg: Any) {
    val future = writeAndFlush(msg)

    future.syncUninterruptibly()
}
