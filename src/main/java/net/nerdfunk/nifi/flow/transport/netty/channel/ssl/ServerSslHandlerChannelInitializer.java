package net.nerdfunk.nifi.flow.transport.netty.channel.ssl;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.SslHandler;
import net.nerdfunk.nifi.flow.transport.netty.channel.StandardChannelInitializer;
import org.apache.nifi.security.util.ClientAuth;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Server SslHandler Channel Initializer for configuring SslHandler with server parameters
 * @param <T> Channel Type
 */
public class ServerSslHandlerChannelInitializer<T extends Channel>  extends StandardChannelInitializer<T> {
    private final SSLContext sslContext;

    private final ClientAuth clientAuth;

    /**
     * Server SSL Channel Initializer with handlers and SSLContext
     *
     * @param handlerSupplier Channel Handler Supplier
     * @param sslContext SSLContext
     */
    public ServerSslHandlerChannelInitializer(final Supplier<List<ChannelHandler>> handlerSupplier, final SSLContext sslContext, final ClientAuth clientAuth) {
        super(handlerSupplier);
        this.sslContext = Objects.requireNonNull(sslContext, "SSLContext is required");
        this.clientAuth = Objects.requireNonNull(clientAuth, "ClientAuth is required");
    }

    @Override
    protected void initChannel(final Channel channel) {
        final ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast(newSslHandler());
        super.initChannel(channel);
    }

    private SslHandler newSslHandler() {
        final SSLEngine sslEngine = sslContext.createSSLEngine();
        sslEngine.setUseClientMode(false);
        if (ClientAuth.REQUIRED.equals(clientAuth)) {
            sslEngine.setNeedClientAuth(true);
        } else if (ClientAuth.WANT.equals(clientAuth)) {
            sslEngine.setWantClientAuth(true);
        }
        return new SslHandler(sslEngine);
    }
}
