package io.github.endzeitbegins.nifi.flowovertcp.testing.tcp

import io.github.endzeitbegins.nifi.flowovertcp.testing.tcp.netty.NettyTestTcpClient

internal interface TestTcpClient {
    val hostname: String
    val port: Int

    fun send(payload: ByteArray)
}

internal fun testTcpClient(hostname: String, port: Int): TestTcpClient = NettyTestTcpClient(hostname, port)
