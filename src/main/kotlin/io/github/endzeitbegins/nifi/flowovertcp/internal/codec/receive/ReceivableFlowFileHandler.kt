package io.github.endzeitbegins.nifi.flowovertcp.internal.codec.receive

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import org.apache.nifi.logging.ComponentLog
import org.apache.nifi.processor.ProcessSessionFactory
import org.apache.nifi.processor.Relationship
import java.util.concurrent.atomic.AtomicReference

/**
 * TODO
 */
internal class ReceivableFlowFileHandler(
    addNetworkInformationAttributes: Boolean,
    processSessionFactoryReference: AtomicReference<ProcessSessionFactory>,
    targetRelationship: Relationship,
    logger: ComponentLog
) : SimpleChannelInboundHandler<ReceivableFlowFile>() {
    override fun channelRead0(ctx: ChannelHandlerContext?, msg: ReceivableFlowFile) {
        // TODO
        //  first iteration, implement session handling in here, similar to Tcp2flowAndAttributesChannelHandler
        //  second iteration, only offer events to a blockingqueue, similar to listentcp;
        //    & handle events in ontrigger of processor
        TODO("Not yet implemented")
    }
}