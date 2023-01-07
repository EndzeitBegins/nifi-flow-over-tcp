package io.github.endzeitbegins.nifi.flowovertcp.testing.tcp.netty

import io.github.endzeitbegins.nifi.flowovertcp.testing.tcp.TestTcpClient
import org.apache.nifi.event.transport.configuration.ShutdownQuietPeriod
import org.apache.nifi.event.transport.configuration.ShutdownTimeout
import org.apache.nifi.event.transport.configuration.TransportProtocol
import org.apache.nifi.event.transport.netty.ByteArrayNettyEventSenderFactory
import org.apache.nifi.mock.MockComponentLogger
import java.time.Duration

internal class NettyTestTcpClient(
    override val hostname: String,
    override val port: Int
) : TestTcpClient {

    private val eventSenderFactory =
        ByteArrayNettyEventSenderFactory(MockComponentLogger(), hostname, port, TransportProtocol.TCP).apply {
            setShutdownQuietPeriod(ShutdownQuietPeriod.QUICK.duration)
            setShutdownTimeout(ShutdownTimeout.QUICK.duration)
            setTimeout(Duration.ofSeconds(30)) // todo
        }

    override fun send(payload: ByteArray) {
        eventSenderFactory.eventSender.use { eventSender ->
            eventSender.sendEvent(payload)
        }
    }
}