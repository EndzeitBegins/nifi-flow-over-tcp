package io.github.endzeitbegins.nifi.flowovertcp.internal.transmission.netty.send

import io.github.endzeitbegins.nifi.flowovertcp.internal.transmission.send.FlowSender
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.pool.ChannelHealthChecker
import io.netty.channel.pool.ChannelPool
import io.netty.channel.pool.ChannelPoolHandler
import io.netty.channel.pool.FixedChannelPool
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.stream.ChunkedWriteHandler
import io.netty.util.concurrent.DefaultThreadFactory
import net.nerdfunk.nifi.flow.transport.FlowException
import net.nerdfunk.nifi.flow.transport.netty.channel.LogExceptionChannelHandler
import net.nerdfunk.nifi.flow.transport.netty.channel.StandardChannelInitializer
import net.nerdfunk.nifi.flow.transport.netty.channel.pool.InitializingChannelPoolHandler
import net.nerdfunk.nifi.flow.transport.netty.channel.ssl.ClientSslStandardChannelInitializer
import net.nerdfunk.nifi.flow.transport.netty.codec.InputStreamFlowHeaderEncoder
import net.nerdfunk.nifi.flow.transport.netty.codec.InputStreamMessageEncoder
import org.apache.nifi.logging.ComponentLog
import java.net.InetSocketAddress
import java.time.Duration
import java.util.function.Supplier
import javax.net.ssl.SSLContext

internal class NettyFlowSender(
    hostname: String,
    port: Int,
    private val separateConnectionPerFlowFile: Boolean,
    maxConcurrentTasks: Int,
    maxSocketSendBufferSize: Int,
    timeout: Duration,
    sslContext: SSLContext?,
    logger: ComponentLog,
    logPrefix: String
) : FlowSender<NettyFlowSenderChannel> {

    private val channelPool: ChannelPool
    private val eventLoopGroup: EventLoopGroup

    init {
        val bootstrap = Bootstrap().apply {
            remoteAddress(InetSocketAddress(hostname, port))

            group(NioEventLoopGroup(maxConcurrentTasks, DefaultThreadFactory(logPrefix, true)))
            channel(NioSocketChannel::class.java)

            option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout.toMillis().toInt())
            option(ChannelOption.SO_SNDBUF, maxSocketSendBufferSize)
        }

        val handlerSupplier = Supplier {
            listOf<ChannelHandler>(
                LogExceptionChannelHandler(logger),
                ChunkedWriteHandler(),
                InputStreamFlowHeaderEncoder(),
                InputStreamMessageEncoder()
            )
        }

        val channelInitializer: StandardChannelInitializer<Channel> =
            if (sslContext == null) StandardChannelInitializer(handlerSupplier)
            else ClientSslStandardChannelInitializer(handlerSupplier, sslContext)
        channelInitializer.setWriteTimeout(timeout)

        val handler: ChannelPoolHandler = InitializingChannelPoolHandler(channelInitializer)

        channelPool = FixedChannelPool(
            bootstrap,
            handler,
            ChannelHealthChecker.ACTIVE,
            FixedChannelPool.AcquireTimeoutAction.FAIL,
            timeout.toMillis(),
            Runtime.getRuntime().availableProcessors() * 2,
            1024
        )

        eventLoopGroup = bootstrap.config().group()
    }

    override fun acquireChannel(): NettyFlowSenderChannel {
        return try {
            val nettyChannelFuture = channelPool.acquire().sync()
            val nettyChannel = nettyChannelFuture.get()

            NettyFlowSenderChannel(nettyChannel)
        } catch (e: Exception) {
            throw FlowException("Failed to acquire channel!", e)
        }
    }

    override fun releaseChannel(channel: NettyFlowSenderChannel) {
        val nettyChannel = channel.nettyChannel

        if (separateConnectionPerFlowFile) {
            nettyChannel.close()
        }
        channelPool.release(nettyChannel)
    }

    override fun close() {
        try {
            channelPool.close()
        } finally {
            eventLoopGroup.shutdownGracefully().syncUninterruptibly()
        }
    }
}