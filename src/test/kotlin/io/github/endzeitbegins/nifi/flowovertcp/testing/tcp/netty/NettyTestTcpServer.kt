package io.github.endzeitbegins.nifi.flowovertcp.testing.tcp.netty

import io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile.toInt
import io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile.toLong
import io.github.endzeitbegins.nifi.flowovertcp.testing.tcp.TestTcpServer
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


class NettyTestTcpServer : TestTcpServer {

    private val receivedBytesMap = mutableMapOf<String, ByteArray>()
    override val receivedBytes: Map<String, ByteArray>
        get() = receivedBytesMap

    private var eventServer: EventServer? = null

    override fun start(port: Int) {
        shutdown()

        val listenAddress: InetAddress = InetAddress.getByName("0.0.0.0")
        val serverFactory = NettyEventServerFactory(listenAddress, port, TransportProtocol.TCP).apply {
            setHandlerSupplier {
                listOf<ChannelHandler>(
                    ByteArrayDecoder(),
                    InMemoryFlowMessageDecoder(receivedBytesMap),
                    LogExceptionChannelHandler(MockComponentLogger())
                )
            }

            setShutdownQuietPeriod(ShutdownQuietPeriod.QUICK.duration)
            setShutdownTimeout(ShutdownTimeout.QUICK.duration)
        }

        eventServer = serverFactory.eventServer
    }

    override fun shutdown() {
        eventServer?.shutdown()
    }

    override fun clearCache() {
        receivedBytesMap.clear()
    }
}

class InMemoryFlowMessageDecoder(
    private val receivedBytesMap: MutableMap<String, ByteArray>
) : SimpleChannelInboundHandler<ByteArray>() {

    private var attributesLength: Int = -1
    private var contentLength: Long = -1
    private var receivedBytes: ByteArray = ByteArray(0)

    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteArray) {
        receivedBytes += msg

        if (attributesLength < 0 || contentLength < 0) {
            attributesLength = receivedBytes.take(4).toInt(byteOrder = ByteOrder.BIG_ENDIAN)
            contentLength = receivedBytes.drop(4).take(8).toLong(byteOrder = ByteOrder.BIG_ENDIAN)
        }

        val expectedSize = attributesLength + contentLength.toInt() + 12
        if (receivedBytes.size >= expectedSize) {
            receivedBytesMap[ctx.channel().id().asLongText()] = receivedBytes.copyOfRange(0, expectedSize)

            receivedBytes = receivedBytes.copyOfRange(expectedSize, receivedBytes.size)
            attributesLength = -1
            contentLength = -1
        }
    }
}
