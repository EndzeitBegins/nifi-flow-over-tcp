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
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * TODO
 */
internal class ReceivableFlowFileHandler(
    private val addNetworkInformationAttributes: Boolean,
    private val processSessionFactoryReference: AtomicReference<ProcessSessionFactory>,
    private val targetRelationship: Relationship,
    private val logger: ComponentLog
) : SimpleChannelInboundHandler<ReceivableFlowFile>() {

    // todo combine?
    private var activeId: String? = null
    private lateinit var session: ProcessSession
    private lateinit var flowFile: FlowFile
    private lateinit var contentStream: OutputStream

    // todo remove ---
    private val start = Instant.now()
//    private var n = 0
    // todo remove ---

    override fun channelRead0(context: ChannelHandlerContext, event: ReceivableFlowFile) {
        if (activeId != event.generatedId) {
            // todo discard old?

            activeId = event.generatedId
            session = claimProcessSession()
            flowFile = session.create()
            contentStream = session.write(flowFile)
        }

        when (event) {
            is ReceivableFlowFileAttributes -> {
                flowFile = session.putAllAttributes(flowFile, event.attributes)
            }

            is ReceivableFlowFileContentFragment -> {
                contentStream.write(event.payload)

//                session.append(flowFile) { outputStream ->
//                    outputStream.write(event.payload)
//                }
            }
        }

        // todo remove ---
//        n++
//        if (n > 100) {
//            logger.warn("got here! + ${event.isLastFragment}")
//        }
        // todo remove ---

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

            contentStream.close()

            // todo nullability?!
            session.provenanceReporter.receive(flowFile, "tcp://${senderAddress!!.hostString}:${senderAddress!!.port}")
            session.transfer(flowFile, targetRelationship)
            session.commitAsync()

            // todo remove ---
            println("Took ${Duration.between(start, Instant.now()).toMillis()}ms!")
            // todo remove ---
        }

        // TODO
        //  first iteration, implement session handling in here, similar to Tcp2flowAndAttributesChannelHandler
        //  second iteration, only offer events to a blockingqueue, similar to listentcp;
        //    & handle events in ontrigger of processor
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
}