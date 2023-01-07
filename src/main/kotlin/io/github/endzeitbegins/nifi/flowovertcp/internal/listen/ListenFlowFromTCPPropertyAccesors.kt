package io.github.endzeitbegins.nifi.flowovertcp.internal.listen

import io.github.endzeitbegins.nifi.flowovertcp.ListenFlowFromTCP
import org.apache.nifi.event.transport.configuration.BufferAllocator
import org.apache.nifi.processor.DataUnit
import org.apache.nifi.processor.ProcessContext
import org.apache.nifi.processor.util.listen.ListenerProperties
import org.apache.nifi.remote.io.socket.NetworkUtils
import org.apache.nifi.security.util.ClientAuth
import org.apache.nifi.ssl.SSLContextService
import java.net.InetAddress
import java.time.Duration
import java.util.concurrent.TimeUnit

internal val ProcessContext.address: InetAddress?
    get() {
        val networkInterface =
            getProperty(ListenerProperties.NETWORK_INTF_NAME).evaluateAttributeExpressions().value

        return NetworkUtils.getInterfaceAddress(networkInterface)
    }

internal val ProcessContext.port: Int
    get() = getProperty(ListenerProperties.PORT).evaluateAttributeExpressions().asInteger()

internal val ProcessContext.addNetworkInformationAttributes: Boolean
    get() = getProperty(ListenFlowFromTCP.ADD_NETWORK_INFORMATION_ATTRIBUTES).asBoolean()

internal val ProcessContext.idleTimeout: Duration
    get() = Duration.ofSeconds(getProperty(ListenFlowFromTCP.IDLE_CONNECTION_TIMEOUT).asTimePeriod(TimeUnit.SECONDS))

internal val ProcessContext.socketBufferSize: Int
    get() = getProperty(ListenerProperties.MAX_SOCKET_BUFFER_SIZE).asDataSize(DataUnit.B).toInt()

internal val ProcessContext.workerThreads: Int
    get() = getProperty(ListenerProperties.WORKER_THREADS).asInteger()

internal val ProcessContext.bufferAllocator: BufferAllocator
    get() {
        val poolReceiveBuffers: Boolean = getProperty(ListenFlowFromTCP.POOL_RECV_BUFFERS).asBoolean()

        return if (poolReceiveBuffers) BufferAllocator.POOLED else BufferAllocator.UNPOOLED
    }

internal fun ProcessContext.sslContextServiceOrNull(): SSLContextService? =
    getProperty(ListenFlowFromTCP.SSL_CONTEXT_SERVICE).asControllerService(
        SSLContextService::class.java
    )

internal val ProcessContext.clientAuth: ClientAuth
    get() {
        val clientAuthValue = getProperty(ListenFlowFromTCP.CLIENT_AUTH).value

        return ClientAuth.valueOf(clientAuthValue)
    }