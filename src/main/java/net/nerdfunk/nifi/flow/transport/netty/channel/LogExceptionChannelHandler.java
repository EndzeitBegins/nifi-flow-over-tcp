package net.nerdfunk.nifi.flow.transport.netty.channel;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.apache.nifi.logging.ComponentLog;

import java.net.SocketAddress;

/**
 * Log Exception Channel Handler for logging exceptions in absence of other handlers
 */
@ChannelHandler.Sharable
public class LogExceptionChannelHandler extends ChannelInboundHandlerAdapter {
    private final ComponentLog log;

    public LogExceptionChannelHandler(final ComponentLog log) {
        this.log = log;
    }

    /**
     * Log Exceptions caught during Channel handling
     *
     * @param context Channel Handler Context
     * @param exception Exception
     */
    @Override
    public void exceptionCaught(final ChannelHandlerContext context, final Throwable exception) {
        final SocketAddress remoteAddress = context.channel().remoteAddress();
        log.warn("Communication Failed with Remote Address [{}]", remoteAddress, exception);
    }
}
