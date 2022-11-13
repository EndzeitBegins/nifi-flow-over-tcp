package net.nerdfunk.nifi.flow.transport.netty.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import net.nerdfunk.nifi.flow.transport.message.FlowMessage;

/**
 * Message encoder for our FlowMessage Header
 */
@ChannelHandler.Sharable
public class InputStreamFlowHeaderEncoder extends MessageToByteEncoder<FlowMessage> {

    /*
     * this encoder encodes the header but NOT the payload. The payload 
     * is encoded by the inputStreamMessageEncoder
     *
     * the encoders are initialized in StreamingNettyFlowSenderFactory
     */
    @Override
    protected void encode(ChannelHandlerContext context, FlowMessage msg, ByteBuf out) throws Exception {

        // write headerlength
        ByteBuf hlByteBuf = context.alloc().buffer(4);
        hlByteBuf.writeInt(msg.getHeaderlength());
        context.write(hlByteBuf);

        // write payloadlength
        ByteBuf plByteBuf = context.alloc().buffer(8);
        plByteBuf.writeLong(msg.getPayloadlength());
        context.write(plByteBuf);

        // writeHeader
        ByteBuf stringByteBuf = context.alloc().buffer(msg.getHeaderlength());
        stringByteBuf.writeBytes(msg.getHeader());
        context.write(stringByteBuf);

        // only when flush is called the data is send to the network stack
        context.flush();
    }
}