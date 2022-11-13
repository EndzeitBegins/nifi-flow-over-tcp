package net.nerdfunk.nifi.flow.transport.tcp2flow;

import io.netty.handler.codec.bytes.ByteArrayDecoder;
import net.nerdfunk.nifi.flow.transport.netty.channel.LogExceptionChannelHandler;
import java.util.Arrays;
import net.nerdfunk.nifi.flow.transport.netty.NettyFlowServerFactory;
import static net.nerdfunk.nifi.processors.ListenTCP2flow.FLOW_AND_ATTRIBUTES;
import net.nerdfunk.nifi.flow.transport.netty.codec.SocketByteArrayMessageDecoder;
import net.nerdfunk.nifi.flow.transport.netty.channel.Tcp2flowAndAttributesChannelHandler;
import net.nerdfunk.nifi.flow.transport.netty.channel.Tcp2flowContentOnlyChannelHandler;

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
        super(tcp2flowconfiguration.getBindAddressAsString() ,
              tcp2flowconfiguration.getPort());

        final LogExceptionChannelHandler logExceptionChannelHandler = new LogExceptionChannelHandler(tcp2flowconfiguration.getLogger());

        if (FLOW_AND_ATTRIBUTES.toString().equalsIgnoreCase(tcp2flowconfiguration.getEncoder())) {

            setHandlerSupplier(() -> Arrays.asList(
                    logExceptionChannelHandler,
                    new Tcp2flowAndAttributesDecoder(tcp2flowconfiguration.getLogger()),
                    new Tcp2flowAndAttributesChannelHandler(tcp2flowconfiguration)
            ));
        }
        else {
            setHandlerSupplier(() -> Arrays.asList(
                    logExceptionChannelHandler,
                    new ByteArrayDecoder(),
                    new SocketByteArrayMessageDecoder(),
                    new Tcp2flowContentOnlyChannelHandler(tcp2flowconfiguration)
            ));
        }
    }
}
