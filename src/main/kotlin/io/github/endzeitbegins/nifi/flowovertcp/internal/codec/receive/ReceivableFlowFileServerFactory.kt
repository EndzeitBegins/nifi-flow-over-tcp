package io.github.endzeitbegins.nifi.flowovertcp.internal.codec.receive

import net.nerdfunk.nifi.flow.transport.netty.channel.Tcp2flowAndAttributesChannelHandler
import net.nerdfunk.nifi.flow.transport.tcp2flow.Tcp2flowAndAttributesDecoder
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

                /** TODO */
                Tcp2flowAndAttributesDecoder(logger),
                /** TODO */
                Tcp2flowAndAttributesChannelHandler(
                    addNetworkInformationAttributes,
                    processSessionFactoryReference,
                    targetRelationship,
                    logger
                ),
            )
        }
    }
}