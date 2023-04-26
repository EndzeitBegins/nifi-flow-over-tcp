package io.github.endzeitbegins.nifi.flowovertcp.internal.codec.receive

import org.apache.nifi.flowfile.FlowFile

/**
 * A chunked representation of a [FlowFile] to receive.
 *
 * See [ReceivableFlowFileDecoder] on how it is decoded from a binary representation.
 */
internal sealed interface ReceivableFlowFile {
    /**
     * A generated ID to uniquely identify the received [FlowFile].
     *
     * Note that this is not the FlowFile UUID used on the sender site.
     */
    val generatedId: String

    /**
     * Signals that this is the last chunk of the received [FlowFile].
     */
    val isLastFragment: Boolean
}

/**
 * A chunk containing the attributes of the received [FlowFile].
 */
internal class ReceivableFlowFileAttributes(
    override val generatedId: String,
    val attributes: Map<String, String?>,
    override val isLastFragment: Boolean,
) : ReceivableFlowFile

/**
 * A chunk containing a fragment of the content of the received [FlowFile].
 */
internal class ReceivableFlowFileContentFragment(
    override val generatedId: String,
    val payload: ByteArray,
    override val isLastFragment: Boolean,
) : ReceivableFlowFile
