package net.nerdfunk.nifi.flow.transport.netty.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;
import org.apache.nifi.event.transport.message.ByteArrayMessage;

import java.net.InetAddress;
import java.util.List;

/**
 * Message Decoder for bytes received from Datagram Packets
 */
public class DatagramByteArrayMessageDecoder extends MessageToMessageDecoder<DatagramPacket> {
    /**
     * Decode Datagram Packet to Byte Array Message
     *
     * @param channelHandlerContext Channel Handler Context
     * @param datagramPacket Datagram Packet
     * @param decoded Decoded Messages
     */
    @Override
    protected void decode(final ChannelHandlerContext channelHandlerContext, final DatagramPacket datagramPacket, final List<Object> decoded) {
        final ByteBuf content = datagramPacket.content();
        final int readableBytes = content.readableBytes();
        final byte[] bytes = new byte[readableBytes];
        content.readBytes(bytes);

        final InetAddress packetAddress = datagramPacket.sender().getAddress();
        final String address = packetAddress.getHostAddress();
        final ByteArrayMessage message = new ByteArrayMessage(bytes, address);
        decoded.add(message);
    }
}
