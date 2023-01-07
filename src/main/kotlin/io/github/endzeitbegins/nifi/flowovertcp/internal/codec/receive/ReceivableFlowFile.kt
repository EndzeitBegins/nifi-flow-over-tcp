package io.github.endzeitbegins.nifi.flowovertcp.internal.codec.receive

import org.apache.nifi.flowfile.FlowFile

/**
 * A chunked representation of a [FlowFile] to receive.
 *
 * See [ReceivableFlowFileDecoder] on how it is decoded from a binary representation.
 */
internal sealed interface ReceivableFlowFile {
    val generatedId: String
}

/**
 * TODO
 */
internal data class ReceivableFlowFileAttributes(
    override val generatedId: String,
    val attributes: Map<String, String>,
) : ReceivableFlowFile

/**
 * TODO
 */
internal data class ReceivableFlowFileContentFragment(
    override val generatedId: String,
    val payload: List<Byte>,
    val isLastFragment: Boolean,
) : ReceivableFlowFile
