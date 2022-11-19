package io.github.endzeitbegins.nifi.flowovertcp.testing.tcp.netty

import io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile.toInt
import io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile.toLong
import io.github.endzeitbegins.nifi.flowovertcp.testing.tcp.TestTcpServer
import io.github.endzeitbegins.nifi.flowovertcp.testing.utils.WatchedMutableSet
import io.github.endzeitbegins.nifi.flowovertcp.testing.utils.watch
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.bytes.ByteArrayDecoder
import org.apache.nifi.event.transport.EventServer
import org.apache.nifi.event.transport.configuration.ShutdownQuietPeriod
import org.apache.nifi.event.transport.configuration.ShutdownTimeout
import org.apache.nifi.event.transport.configuration.TransportProtocol
import org.apache.nifi.event.transport.netty.NettyEventServerFactory
import org.apache.nifi.event.transport.netty.channel.LogExceptionChannelHandler
import org.apache.nifi.mock.MockComponentLogger
import java.net.InetAddress
import java.nio.ByteOrder
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class NettyTestTcpServer : TestTcpServer {

    private val receivedBytesMap: MutableMap<String, ByteArray> = ConcurrentHashMap<String, ByteArray>()
    private val activeChannels: WatchedMutableSet<String> = ConcurrentHashMap.newKeySet<String>().watch()
    private val timeout = Duration.ofSeconds(15)

    override val receivedBytes: Map<String, ByteArray>
        get() {
            ensureAtLeastOneChannelWasActive()
            ensureNoChannelIsActive()

            return receivedBytesMap
        }

    private lateinit var eventServer: EventServer

    override fun start(port: Int) {
        val listenAddress: InetAddress = InetAddress.getByName("0.0.0.0")
        val serverFactory = NettyEventServerFactory(listenAddress, port, TransportProtocol.TCP).apply {
            setHandlerSupplier {
                listOf<ChannelHandler>(
                    ByteArrayDecoder(),
                    InMemoryFlowMessageDecoder(receivedBytesMap, activeChannels),
                    LogExceptionChannelHandler(MockComponentLogger())
                )
            }

            setShutdownQuietPeriod(ShutdownQuietPeriod.QUICK.duration)
            setShutdownTimeout(ShutdownTimeout.QUICK.duration)
        }

        eventServer = serverFactory.eventServer
    }

    override fun shutdown() {
        eventServer.shutdown()
    }

    private fun ensureAtLeastOneChannelWasActive() =
        ensureThat({ activeChannels.didContainElement }) {
            IllegalStateException("There was not a single channel active!")
        }

    private fun ensureNoChannelIsActive() =
        ensureThat({ activeChannels.isEmpty() }) {
            IllegalStateException("There were active channels $activeChannels left over!")
        }

    private fun ensureThat(predicate: () -> Boolean, onTimeout: () -> Throwable) {
        fun nowMillis() = Instant.now().toEpochMilli()

        val timeoutMillis = timeout.toMillis()
        val startMillis = nowMillis()

        var conditionMet = predicate.invoke()

        while (!conditionMet && (nowMillis() - startMillis) < timeoutMillis) {
            Thread.sleep(25)

            conditionMet = predicate.invoke()
        }

        if (!conditionMet) {
            throw onTimeout.invoke()
        }
    }
}

class InMemoryFlowMessageDecoder(
    private val receivedBytesMap: MutableMap<String, ByteArray>,
    private val activeChannels: MutableSet<String>
) : SimpleChannelInboundHandler<ByteArray>() {

    private var attributesLength: Int = -1
    private var contentLength: Long = -1
    private var receivedBytes: ByteArray = ByteArray(0)

    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteArray) {
        val channelId = ctx.channel().id().asLongText()
        activeChannels.add(channelId)

        receivedBytes += msg

        if (attributesLength < 0 || contentLength < 0) {
            attributesLength = receivedBytes.take(4).toInt(byteOrder = ByteOrder.BIG_ENDIAN)
            contentLength = receivedBytes.drop(4).take(8).toLong(byteOrder = ByteOrder.BIG_ENDIAN)
        }

        val expectedSize = attributesLength + contentLength.toInt() + 12
        if (receivedBytes.size >= expectedSize) {
            receivedBytesMap[channelId] = receivedBytes.copyOfRange(0, expectedSize)

            receivedBytes = receivedBytes.copyOfRange(expectedSize, receivedBytes.size)
            attributesLength = -1
            contentLength = -1

            if (receivedBytes.isEmpty()) {
                activeChannels.remove(channelId)
            }
        }
    }
}
