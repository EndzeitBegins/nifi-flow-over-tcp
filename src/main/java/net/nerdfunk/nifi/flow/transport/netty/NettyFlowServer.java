package net.nerdfunk.nifi.flow.transport.netty;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import net.nerdfunk.nifi.flow.transport.FlowServer;

/**
 * Netty Flow Server
 */
class NettyFlowServer implements FlowServer {
    private final EventLoopGroup group;

    private final Channel channel;

    /**
     * Netty Flow Server with Flow Loop Group and bound Channel
     *
     * @param group Flow Loop Group
     * @param channel Bound Channel
     */
    NettyFlowServer(final EventLoopGroup group, final Channel channel) {
        this.group = group;
        this.channel = channel;
    }

    /**
     * Close Channel and shutdown Event Loop Group
     */
    @Override
    public void shutdown() {
        try {
            if (channel.isOpen()) {
                channel.close().syncUninterruptibly();
            }
        } finally {
            group.shutdownGracefully().syncUninterruptibly();
        }
    }
}
