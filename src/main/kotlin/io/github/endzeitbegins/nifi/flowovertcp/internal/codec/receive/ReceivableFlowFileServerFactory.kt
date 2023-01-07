package io.github.endzeitbegins.nifi.flowovertcp.internal.codec.receive

import io.github.endzeitbegins.nifi.flowovertcp.internal.codec.send.TransmittableFlowFile
import io.netty.channel.ChannelHandler
import io.netty.handler.stream.ChunkedStream
import org.apache.nifi.event.transport.configuration.TransportProtocol
import org.apache.nifi.event.transport.netty.NettyEventServerFactory
import org.apache.nifi.event.transport.netty.channel.LogExceptionChannelHandler
import org.apache.nifi.logging.ComponentLog
import org.apache.nifi.processor.ProcessSessionFactory
import org.apache.nifi.processor.Processor
import org.apache.nifi.processor.Relationship
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicReference

internal class ReceivableFlowFileServerFactory(
    address: InetAddress?,
    port: Int,
    protocol: TransportProtocol,

    // TODO could get rid of those, when handling "events" in onTrigger instead .. pass BlockingQueue of events instead
    addNetworkInformationAttributes: Boolean,
    processSessionFactoryReference: AtomicReference<ProcessSessionFactory>,
    targetRelationship: Relationship,

    logger: ComponentLog,
) : NettyEventServerFactory(address, port, protocol) {

    init {
        setHandlerSupplier {
            listOf(
                /** a standard [LogExceptionChannelHandler] provided by nifi-event-transport,
                 * that allows logging in the [ComponentLog] of the integrating [Processor] */
                LogExceptionChannelHandler(logger),

                /** a custom [ChannelHandler] that converts the received bytes into [ReceivableFlowFile] objects */
                ReceivableFlowFileDecoder(logger),
                /** a custom [ChannelHandler] that ... [ReceivableFlowFile] objects ... TODO */
                ReceivableFlowFileHandler(
                    addNetworkInformationAttributes,
                    processSessionFactoryReference,
                    targetRelationship,
                    logger
                ),
            )
        }
    }
}