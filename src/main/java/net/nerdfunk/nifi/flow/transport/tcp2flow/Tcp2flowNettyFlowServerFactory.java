package net.nerdfunk.nifi.flow.transport.tcp2flow;

import net.nerdfunk.nifi.flow.transport.netty.NettyFlowServerFactory;
import net.nerdfunk.nifi.flow.transport.netty.channel.Tcp2flowAndAttributesChannelHandler;
import org.apache.nifi.event.transport.netty.channel.LogExceptionChannelHandler;

import java.util.Arrays;

/**
 * Netty Event Server Factory for Byte Array Messages
 */
public class Tcp2flowNettyFlowServerFactory extends NettyFlowServerFactory {

    /**
     * Netty Event Server Factory to receive a flow with attributes via TCP
     *
     * @param tcp2flowconfiguration Tcp2flowConfiguration
     */
    public Tcp2flowNettyFlowServerFactory(final Tcp2flowConfiguration tcp2flowconfiguration) {
        super(tcp2flowconfiguration.getBindAddress() ,
              tcp2flowconfiguration.getPort());

        final LogExceptionChannelHandler logExceptionChannelHandler = new LogExceptionChannelHandler(tcp2flowconfiguration.getLogger());

        setHandlerSupplier(() -> Arrays.asList(
                logExceptionChannelHandler,
                new Tcp2flowAndAttributesDecoder(tcp2flowconfiguration.getLogger()),
                new Tcp2flowAndAttributesChannelHandler(tcp2flowconfiguration)
        ));
    }
}
