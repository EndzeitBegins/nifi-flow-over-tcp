package io.github.endzeitbegins.nifi.flowovertcp.testing.tcp

import org.apache.mina.core.buffer.IoBuffer
import org.apache.mina.core.service.IoHandlerAdapter
import org.apache.mina.core.session.IoSession
import org.apache.mina.transport.socket.nio.NioSocketAcceptor
import java.net.InetSocketAddress

internal class MinaTestTcpServer: TestTcpServer {

    private val bytes = mutableMapOf<Long, ByteArray>()
    override val receivedBytes: Map<Long, ByteArray> = bytes

    private val socketAcceptor = NioSocketAcceptor()

    init {
        socketAcceptor.handler = object : IoHandlerAdapter() {
            override fun messageReceived(session: IoSession, message: Any) {
                val batch = (message as IoBuffer).asInputStream().readBytes()

                bytes[session.id] = (bytes[session.id] ?: byteArrayOf()) + batch
            }
        }
    }

    override fun start(port: Int) {
        val socketAddress = InetSocketAddress(port)

        socketAcceptor.bind(socketAddress)
    }

    override fun shutdown() {
        socketAcceptor.unbind()
    }

    override fun clearCache() {
        bytes.clear()
    }
}