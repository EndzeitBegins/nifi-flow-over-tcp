package io.github.endzeitbegins.nifi.flowovertcp.internal.codec.receive

import org.apache.nifi.flowfile.FlowFile

/**
 * A chunked representation of a [FlowFile] to receive.
 *
 * See [ReceivableFlowFileDecoder] on how it is decoded from a binary representation.
 */
internal sealed interface ReceivableFlowFile {
    val generatedId: String

    val isLastFragment: Boolean
}

/**
 * TODO
 */
internal class ReceivableFlowFileAttributes(
    override val generatedId: String,
    val attributes: Map<String, String>,
    override val isLastFragment: Boolean,
) : ReceivableFlowFile

/**
 * TODO
 */
internal class ReceivableFlowFileContentFragment(
    override val generatedId: String,
    val payload: ByteArray,
    override val isLastFragment: Boolean,
) : ReceivableFlowFile
