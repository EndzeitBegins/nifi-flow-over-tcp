package io.github.endzeitbegins.nifi.flowovertcp.internal.transmission.send

import io.github.endzeitbegins.nifi.flowovertcp.internal.transmission.netty.send.NettyFlowSender
import org.apache.nifi.logging.ComponentLog
import org.apache.nifi.processor.Processor
import java.time.Duration
import javax.net.ssl.SSLContext

/**
 * A factory for providing a [FlowSender].
 *
 * Using the factory decouples our [Processor] implementation from the concrete network-library used.
 */
internal fun createFlowSender(
    hostname: String,
    port: Int,

    separateConnectionPerFlowFile: Boolean,

    maxConcurrentTasks: Int,
    maxSocketSendBufferSize: Int,
    timeout: Duration,
    sslContext: SSLContext?,

    logger: ComponentLog,
    logPrefix: String,
): FlowSender<out FlowSenderChannel> = NettyFlowSender(
    hostname = hostname,
    port = port,
    separateConnectionPerFlowFile = separateConnectionPerFlowFile,
    maxConcurrentTasks = maxConcurrentTasks,
    maxSocketSendBufferSize = maxSocketSendBufferSize,
    timeout = timeout,
    sslContext = sslContext,
    logger = logger,
    logPrefix = logPrefix,
)