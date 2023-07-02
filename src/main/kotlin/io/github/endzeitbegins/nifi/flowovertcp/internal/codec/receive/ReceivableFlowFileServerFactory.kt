package io.github.endzeitbegins.nifi.flowovertcp.internal.codec.receive

import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.ipfilter.AbstractRemoteAddressFilter
import org.apache.nifi.event.transport.configuration.TransportProtocol
import org.apache.nifi.event.transport.netty.NettyEventServerFactory
import org.apache.nifi.event.transport.netty.channel.LogExceptionChannelHandler
import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.logging.ComponentLog
import org.apache.nifi.processor.ProcessContext
import org.apache.nifi.processor.ProcessSession
import org.apache.nifi.processor.ProcessSessionFactory
import org.apache.nifi.processor.Processor
import org.apache.nifi.processor.Relationship
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicReference

internal class ReceivableFlowFileServerFactory(
    address: InetAddress?,
    port: Int,
    protocol: TransportProtocol,

    addNetworkInformationAttributes: Boolean,
    processSessionFactoryReference: AtomicReference<out ProcessSessionFactory?>,
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
                /** a custom [ChannelHandler] that accepts [ReceivableFlowFile] objects and transforms them into [FlowFile]s */
                ReceivableFlowFileHandler(
                    addNetworkInformationAttributes,
                    processSessionFactoryReference,
                    targetRelationship,
                ),
            )
        }
    }
}

