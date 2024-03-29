package io.github.endzeitbegins.nifi.flowovertcp.internal.codec.receive

import io.github.endzeitbegins.nifi.flowovertcp.internal.codec.parseJsonMap
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.logging.ComponentLog

/**
 * A custom [ChannelHandler] that converts the received bytes into [ReceivableFlowFile] objects.
 *
 * Use in conjunction with [ReceivableFlowFileHandler] to receive [FlowFile]s with arbitrary large content.
 */
internal class ReceivableFlowFileDecoder(
    private val logger: ComponentLog,
) : ByteToMessageDecoder() {

    var state: ReceivableFlowFileDecoderState =
        ReceivableFlowFileDecoderState.ParseAttributesLength()

    override fun decode(context: ChannelHandlerContext, buffer: ByteBuf, out: MutableList<Any>) {
        val readableBytes = buffer.readableBytes()

        state = when (val currentState = state) {
            is ReceivableFlowFileDecoderState.ParseAttributesLength -> {
                if (readableBytes < Int.SIZE_BYTES)
                    return

                buffer.parseAttributesLength(currentState)
            }

            is ReceivableFlowFileDecoderState.ParseContentLength -> {
                if (readableBytes < Long.SIZE_BYTES)
                    return

                buffer.parseContentLength(currentState)
            }

            is ReceivableFlowFileDecoderState.ParseAttributes -> {
                if (readableBytes < currentState.attributesLength)
                    return

                buffer.parseAttributes(currentState, out)
            }

            is ReceivableFlowFileDecoderState.ParseContent -> {
                buffer.parseContent(currentState, readableBytes, out)
            }
        }
    }

    private fun ByteBuf.parseAttributesLength(
        currentState: ReceivableFlowFileDecoderState.ParseAttributesLength
    ): ReceivableFlowFileDecoderState {
        markReaderIndex()
        val attributesLength = readInt()

        logger.debug("Parsed attributes length of $attributesLength bytes.")

        return currentState.transform(attributesLength)
    }

    private fun ByteBuf.parseContentLength(
        currentState: ReceivableFlowFileDecoderState.ParseContentLength
    ): ReceivableFlowFileDecoderState {
        markReaderIndex()
        val contentLength = readLong()

        logger.debug("Parsed content length of $contentLength bytes.")

        return currentState.transform(contentLength)
    }

    private fun ByteBuf.parseAttributes(
        currentState: ReceivableFlowFileDecoderState.ParseAttributes,
        out: MutableList<Any>
    ): ReceivableFlowFileDecoderState {
        if (currentState.attributesLength > 0) {
            markReaderIndex()
            val attributeBytes = ByteArray(currentState.attributesLength)
            readBytes(attributeBytes)

            val attributesJson = attributeBytes.decodeToString()
            val attributes: Map<String, String?> = attributesJson.parseJsonMap()

            logger.debug("Parsed attributes: $attributes")

            val event = ReceivableFlowFileAttributes(
                generatedId = currentState.generatedId,
                attributes = attributes,
                isLastFragment = currentState.contentLength <= 0
            )
            out.add(event)
        }

        return currentState.transform()
    }

    private fun ByteBuf.parseContent(
        currentState: ReceivableFlowFileDecoderState.ParseContent,
        readableBytes: Int,
        out: MutableList<Any>
    ): ReceivableFlowFileDecoderState {
        val missingContentBytes = currentState.contentLength - currentState.contentBytesReceived
        val contentBytesToRead = readableBytes.takeUnless { readableBytes > missingContentBytes }
            ?: missingContentBytes.toInt()

        markReaderIndex()
        val contentFragmentBytes = ByteArray(contentBytesToRead)
        readBytes(contentFragmentBytes)

        logger.debug("Parsed content fragment of $contentBytesToRead bytes.")

        val event = ReceivableFlowFileContentFragment(
            generatedId = currentState.generatedId,
            payload = contentFragmentBytes,
            isLastFragment = contentBytesToRead >= missingContentBytes,
        )
        out.add(event)

        return currentState.transform(contentBytesRead = contentBytesToRead)
    }
}
