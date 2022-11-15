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
import org.apache.nifi.event.transport.message.ByteArrayMessage
import org.apache.nifi.event.transport.netty.NettyEventServerFactory
import org.apache.nifi.event.transport.netty.channel.LogExceptionChannelHandler
import org.apache.nifi.event.transport.netty.codec.SocketByteArrayMessageDecoder
import org.apache.nifi.mock.MockComponentLogger
import java.net.InetAddress
import java.nio.ByteOrder


class NettyTestTcpServer : TestTcpServer {

    private val receivedBytesMap = mutableMapOf<String, ByteArray>()
    override val receivedBytes: Map<String, ByteArray> get() {
        return receivedBytesMap
    }

    private var eventServer: EventServer? = null

    override fun start(port: Int) {
        shutdown()

        val listenAddress: InetAddress = InetAddress.getByName("0.0.0.0")
        val serverFactory = NettyEventServerFactory(listenAddress, port, TransportProtocol.TCP).apply {
            setHandlerSupplier {
                listOf<ChannelHandler>(
                    ByteArrayDecoder(),
                    SocketByteArrayMessageDecoder(),
                    InMemoryFlowMessageDecoder { id, bytes ->
                        receivedBytesMap[id] = bytes.toByteArray()
                    },
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

class InMemoryFlowMessageDecoder(private val receiver: (id: String, bytes: List<Byte>) -> Unit) : SimpleChannelInboundHandler<ByteArrayMessage>() {

    private var attributesLength: Int = -1
    private var contentLength: Long = -1
    private var receivedBytes = listOf<Byte>()

    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteArrayMessage) {
        receivedBytes = receivedBytes + msg.message.toList()

        if (attributesLength < 0 || contentLength < 0) {
            attributesLength = receivedBytes.take(4).toInt(byteOrder = ByteOrder.BIG_ENDIAN)
            contentLength = receivedBytes.drop(4).take(8).toLong(byteOrder = ByteOrder.BIG_ENDIAN)
        }

        val expectedSize = attributesLength + contentLength.toInt() + 12
        if (receivedBytes.size >= expectedSize) {
            receiver.invoke(ctx.channel().id().asLongText(), receivedBytes.take(expectedSize))

            receivedBytes = receivedBytes.drop(expectedSize)
            attributesLength = -1
            contentLength = -1
        }
    }
}
