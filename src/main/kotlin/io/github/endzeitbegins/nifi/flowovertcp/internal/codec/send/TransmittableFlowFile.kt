package io.github.endzeitbegins.nifi.flowovertcp.internal.codec.send

import io.github.endzeitbegins.nifi.flowovertcp.internal.attributes.Attributes
import org.apache.nifi.flowfile.FlowFile
import java.io.InputStream

/**
 * A simplified representation of a [FlowFile] to transmit.
 *
 * See [TransmittableFlowFileHandler] on how it is encoded into a binary representation.
 */
public class TransmittableFlowFile(
    public val attributes: Attributes,
    public val contentLength: Long,
    public val contentStream: InputStream,
)
