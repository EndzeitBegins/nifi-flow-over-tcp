package io.github.endzeitbegins.nifi.flowovertcp.testing.tcp

import io.github.endzeitbegins.nifi.flowovertcp.testing.tcp.netty.NettyTestTcpServer

internal interface TestTcpServer {
    val receivedBytes: Map<String, ByteArray>
    val listeningPort: Int

    fun start(port: Int? = null)

    fun shutdown()
}

internal fun testTcpServer(): TestTcpServer = NettyTestTcpServer()
