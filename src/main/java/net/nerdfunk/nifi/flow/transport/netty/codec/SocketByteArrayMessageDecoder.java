package net.nerdfunk.nifi.flow.transport.netty.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import net.nerdfunk.nifi.flow.transport.message.ByteArrayMessage;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * Message Decoder for bytes received from Socket Channels
 */
public class SocketByteArrayMessageDecoder extends MessageToMessageDecoder<byte[]> {
    /**
     * Decode bytes to Byte Array Message with remote address from Channel.remoteAddress()
     *
     * @param channelHandlerContext Channel Handler Context
     * @param bytes Message Bytes
     * @param decoded Decoded Messages
     */
    @Override
    protected void decode(final ChannelHandlerContext channelHandlerContext, final byte[] bytes, final List<Object> decoded) {
        final InetSocketAddress remoteAddress = (InetSocketAddress) channelHandlerContext.channel().remoteAddress();
        final String address = remoteAddress.getHostString();
        final ByteArrayMessage message = new ByteArrayMessage(bytes, address);
        decoded.add(message);
    }
}
