package net.nerdfunk.nifi.flow.transport.netty.channel.ssl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.SslHandler;
import net.nerdfunk.nifi.flow.transport.netty.channel.StandardChannelInitializer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Client SSL Standard Channel Initializer supporting TLS with SSLContext configuration
 * @param <T> Channel Type
 */
public class ClientSslStandardChannelInitializer<T extends Channel> extends StandardChannelInitializer<T> {
    private final SSLContext sslContext;

    /**
     * Client SSL Channel Initializer with handlers and SSLContext
     *
     * @param handlerSupplier Channel Handler Supplier
     * @param sslContext SSLContext
     */
    public ClientSslStandardChannelInitializer(final Supplier<List<ChannelHandler>> handlerSupplier, final SSLContext sslContext) {
        super(handlerSupplier);
        this.sslContext = Objects.requireNonNull(sslContext, "SSLContext is required");
    }

    @Override
    protected void initChannel(final Channel channel) {
        final ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast(newSslHandler());
        super.initChannel(channel);
    }

    private SslHandler newSslHandler() {
        final SSLEngine sslEngine = sslContext.createSSLEngine();
        sslEngine.setUseClientMode(true);
        return new SslHandler(sslEngine);
    }
}
