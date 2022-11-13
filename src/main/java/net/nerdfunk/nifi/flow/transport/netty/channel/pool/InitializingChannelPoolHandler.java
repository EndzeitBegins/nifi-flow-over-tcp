package net.nerdfunk.nifi.flow.transport.netty.channel.pool;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.pool.AbstractChannelPoolHandler;

import java.util.Objects;

/**
 * Initializing Channel Pool Handler adds Channel Initializer when a Channel is created
 */
public class InitializingChannelPoolHandler extends AbstractChannelPoolHandler {
    private final ChannelInitializer<Channel> channelInitializer;

    /**
     * Initializing Channel Pool Handler
     *
     * @param channelInitializer Channel Initializer
     */
    public InitializingChannelPoolHandler(final ChannelInitializer<Channel> channelInitializer) {
        this.channelInitializer = Objects.requireNonNull(channelInitializer);
    }

    /**
     * Connect Channel when created
     *
     * @param channel Channel to be connected
     */
    @Override
    public void channelCreated(final Channel channel) {
        channel.pipeline().addLast(channelInitializer);
    }
}
