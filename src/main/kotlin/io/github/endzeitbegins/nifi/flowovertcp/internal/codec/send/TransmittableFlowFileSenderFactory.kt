package io.github.endzeitbegins.nifi.flowovertcp.internal.codec.send

import io.netty.channel.ChannelHandler
import io.netty.handler.stream.ChunkedStream
import io.netty.handler.stream.ChunkedWriteHandler
import org.apache.nifi.event.transport.configuration.TransportProtocol
import org.apache.nifi.event.transport.netty.NettyEventSenderFactory
import org.apache.nifi.event.transport.netty.channel.LogExceptionChannelHandler
import org.apache.nifi.logging.ComponentLog
import org.apache.nifi.processor.Processor

internal class TransmittableFlowFileSenderFactory(
    address: String,
    port: Int,
    protocol: TransportProtocol,
    logger: ComponentLog,
) : NettyEventSenderFactory<TransmittableFlowFile>(address, port, protocol) {

    init {
        setHandlerSupplier {
            listOf(
                /** a standard [LogExceptionChannelHandler] provided by nifi-event-transport,
                 * that allows logging in the [ComponentLog] of the integrating [Processor] */
                LogExceptionChannelHandler(logger),
                /** a standard [ChannelHandler] provided by Netty, that allows transmission of [ChunkedStream]s */
                ChunkedWriteHandler(),

                /** a custom [ChannelHandler] that converts a [TransmittableFlowFile] into a [ChunkedStream] */
                TransmittableFlowFileHandler,
            )
        }
    }
}