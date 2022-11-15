package io.github.endzeitbegins.nifi.flowovertcp.testing.tcp

import io.github.endzeitbegins.nifi.flowovertcp.testing.tcp.netty.NettyTestTcpServer

internal interface TestTcpServer {
    val receivedBytes: Map<String, ByteArray>
    fun start(port: Int)
    fun shutdown()
    fun clearCache()
}

internal fun testTcpServer(): TestTcpServer = NettyTestTcpServer()
