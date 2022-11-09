package io.github.endzeitbegins.nifi.flowovertcp.testing.tcp

internal interface TestTcpServer {
    val receivedBytes: Map<Long, ByteArray>
    fun start(port: Int)
    fun shutdown()
    fun clearCache()
}

internal fun testTcpServer(): TestTcpServer = MinaTestTcpServer()