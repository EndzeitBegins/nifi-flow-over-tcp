package io.github.endzeitbegins.nifi.flowovertcp.internal.codec.receive

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.logging.ComponentLog
import org.apache.nifi.processor.ProcessSession
import org.apache.nifi.processor.ProcessSessionFactory
import org.apache.nifi.processor.Relationship
import java.io.OutputStream
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

/**
 * TODO
 *  Add documentation
 *  Refactor to use event passing, move session handling to onTrigger
 */
internal class ReceivableFlowFileHandler(
    private val addNetworkInformationAttributes: Boolean,
    private val processSessionFactoryReference: AtomicReference<ProcessSessionFactory>,
    private val targetRelationship: Relationship,
    private val logger: ComponentLog
) : SimpleChannelInboundHandler<ReceivableFlowFile>() {

    private var activeId: String? = null
    private lateinit var session: ProcessSession
    private lateinit var flowFile: FlowFile
    private var contentStream: OutputStream? = null

    override fun channelRead0(context: ChannelHandlerContext, event: ReceivableFlowFile) {
        if (activeId != event.generatedId) {
            activeId = event.generatedId
            session = claimProcessSession()
            flowFile = session.create()

            contentStream?.close()
            contentStream = null
        }

        when (event) {
            is ReceivableFlowFileAttributes -> {
                contentStream?.close()
                contentStream = null

                flowFile = session.putAllAttributes(flowFile, event.attributes)
            }

            is ReceivableFlowFileContentFragment -> {
                contentStream = (contentStream ?: session.write(flowFile)).also { outputStream ->
                    outputStream.write(event.payload)
                }
            }
        }

        if (event.isLastFragment) {
            val channel = context.channel()
            val senderAddress = channel.remoteAddress() as? InetSocketAddress
            val receiverAddress = channel.localAddress() as? InetSocketAddress

            if (addNetworkInformationAttributes) {
                val networkInformationAttributes = buildMap {
                    if (senderAddress != null) {
                        put("tcp.sender", senderAddress.address.hostAddress)
                    }
                    if (receiverAddress != null) {
                        put("tcp.receiver", receiverAddress.address.hostAddress)
                        put("tcp.receiver_port", "${receiverAddress.port}")
                    }
                }

                flowFile = session.putAllAttributes(flowFile, networkInformationAttributes)
            }

            contentStream?.close()
            contentStream = null

            session.provenanceReporter.receive(flowFile, senderAddress?.toTransitUri() ?: "unknown")
            session.transfer(flowFile, targetRelationship)
            session.commitAsync()
        }
    }

    private fun claimProcessSession(): ProcessSession {
        return getProcessSessionFactory().createSession()
    }

    private fun getProcessSessionFactory(): ProcessSessionFactory {
        var processSessionFactory: ProcessSessionFactory? = processSessionFactoryReference.get()
        while (processSessionFactory == null) {
            Thread.sleep(100)
            processSessionFactory = processSessionFactoryReference.get()
        }

        return processSessionFactory
    }

    private fun InetSocketAddress.toTransitUri(): String {
        return "tcp://${hostString}:${port}"
    }
}
