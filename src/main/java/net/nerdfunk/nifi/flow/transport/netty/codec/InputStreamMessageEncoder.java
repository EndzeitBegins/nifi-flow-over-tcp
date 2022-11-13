package net.nerdfunk.nifi.flow.transport.netty.codec;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.stream.ChunkedStream;

import java.io.InputStream;
import java.util.List;

/**
 * Message encoder for an InputStream, which wraps the stream in a ChunkedStream for use with a ChunkedWriter. Can add a delimiter
 * to the end of the output objects if the InputStream is a DelimitedInputStream.
 */
@ChannelHandler.Sharable
public class InputStreamMessageEncoder extends MessageToMessageEncoder<InputStream> {

    @Override
    protected void encode(ChannelHandlerContext context, InputStream messageStream, List<Object> out) throws Exception {
        ChunkedStream chunkedMessage = new ChunkedStream(messageStream);
        out.add(chunkedMessage);
    }
}
