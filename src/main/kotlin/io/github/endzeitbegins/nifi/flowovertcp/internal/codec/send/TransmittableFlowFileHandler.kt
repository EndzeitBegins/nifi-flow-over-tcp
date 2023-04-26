package io.github.endzeitbegins.nifi.flowovertcp.internal.codec.send

import io.github.endzeitbegins.nifi.flowovertcp.internal.codec.toJsonMap
import io.github.endzeitbegins.nifi.flowovertcp.internal.utils.chunked
import io.github.endzeitbegins.nifi.flowovertcp.internal.utils.concatenate
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageEncoder
import io.netty.handler.stream.ChunkedStream
import io.netty.handler.stream.ChunkedWriteHandler
import org.apache.nifi.flowfile.FlowFile
import java.io.InputStream
import java.nio.ByteOrder


/**
 * A custom [ChannelHandler] that converts a [TransmittableFlowFile] into a [ChunkedStream].
 *
 * Use in conjunction with [ChunkedWriteHandler] to stream [FlowFile]s with arbitrary large content.
 */
@ChannelHandler.Sharable
internal object TransmittableFlowFileHandler : MessageToMessageEncoder<TransmittableFlowFile>() {


    override fun encode(ctx: ChannelHandlerContext, msg: TransmittableFlowFile, out: MutableList<Any>) {
        val attributesJson = msg.attributes.toJsonMap()
        val attributesBytes = attributesJson.encodeToByteArray()

        val attributesLength: Int = attributesBytes.size
        val contentLength: Long = msg.contentLength

        val headerBytes = attributesLength.toByteRepresentation(byteOrder = ByteOrder.BIG_ENDIAN) +
                contentLength.toByteRepresentation(byteOrder = ByteOrder.BIG_ENDIAN)

        val headerStream: InputStream = headerBytes.inputStream()
        val attributesStream: InputStream = attributesBytes.inputStream()

        val combinedStream = listOf(headerStream, attributesStream, msg.contentStream).concatenate()
        val chunkedStream = combinedStream.chunked()

        out.add(chunkedStream)
    }
}