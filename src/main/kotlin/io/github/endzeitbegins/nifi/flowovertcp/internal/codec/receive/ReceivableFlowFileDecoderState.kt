package io.github.endzeitbegins.nifi.flowovertcp.internal.codec.receive

import java.util.*

/**
 * TODO
 */
internal sealed interface ReceivableFlowFileDecoderState {

    val generatedId: String

    data class ParseAttributesLength(
        override val generatedId: String = "${UUID.randomUUID()}"
    ) : ReceivableFlowFileDecoderState {
        fun transform(attributesLength: Int) = ParseContentLength(
            generatedId = generatedId,
            attributesLength = attributesLength
        )
    }

    data class ParseContentLength(
        override val generatedId: String,
        override val attributesLength: Int,
    ) : KnowsAttributesLength, ReceivableFlowFileDecoderState {
        fun transform(contentLength: Long) = ParseAttributes(
            generatedId = generatedId,
            attributesLength = attributesLength,
            contentLength = contentLength
        )
    }

    data class ParseAttributes(
        override val generatedId: String,
        override val attributesLength: Int,
        override val contentLength: Long,
    ) : KnowsAttributesLength, KnowsContentLength, ReceivableFlowFileDecoderState {
        fun transform(contentBytesReceived: Long) = ParseContent(
            generatedId = generatedId,
            contentLength = contentLength,
            contentBytesReceived = contentBytesReceived
        )
    }

    data class ParseContent(
        override val generatedId: String,
        override val contentLength: Long,
        val contentBytesReceived: Long,
    ) : KnowsContentLength, ReceivableFlowFileDecoderState {
        fun transform(contentBytesRead: Int): ReceivableFlowFileDecoderState =
            if (contentBytesReceived + contentBytesRead >= contentLength) {
                // the whole FlowFile content has been read, start reading the next FlowFile
                ParseAttributesLength()
            } else {
                ParseContent(
                    generatedId = generatedId,
                    contentLength = contentLength,
                    contentBytesReceived = contentBytesReceived + contentBytesRead
                )
            }
    }

    interface KnowsAttributesLength {
        val attributesLength: Int
    }

    interface KnowsContentLength {
        val contentLength: Long
    }
}