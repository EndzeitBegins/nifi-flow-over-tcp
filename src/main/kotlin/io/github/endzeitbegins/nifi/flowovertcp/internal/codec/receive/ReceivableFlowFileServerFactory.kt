package io.github.endzeitbegins.nifi.flowovertcp.internal.codec.receive

import net.nerdfunk.nifi.flow.transport.netty.channel.Tcp2flowAndAttributesChannelHandler
import net.nerdfunk.nifi.flow.transport.tcp2flow.Tcp2flowAndAttributesDecoder
import net.nerdfunk.nifi.flow.transport.tcp2flow.Tcp2flowConfiguration
import org.apache.nifi.event.transport.configuration.TransportProtocol
import org.apache.nifi.event.transport.netty.NettyEventServerFactory
import org.apache.nifi.event.transport.netty.channel.LogExceptionChannelHandler
import org.apache.nifi.logging.ComponentLog
import org.apache.nifi.processor.Processor
import java.net.InetAddress

internal class ReceivableFlowFileServerFactory(
    address: InetAddress?,
    port: Int,
    protocol: TransportProtocol,
    logger: ComponentLog,

    tcp2flowconfiguration: Tcp2flowConfiguration,  // TODO
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
                Tcp2flowAndAttributesChannelHandler(tcp2flowconfiguration, tcp2flowconfiguration.addIpAndPort, logger),
            )
        }
    }
}