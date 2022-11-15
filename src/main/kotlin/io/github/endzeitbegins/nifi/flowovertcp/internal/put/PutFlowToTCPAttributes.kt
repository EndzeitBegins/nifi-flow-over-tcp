package io.github.endzeitbegins.nifi.flowovertcp.internal.put

import io.github.endzeitbegins.nifi.flowovertcp.PutFlowToTCP
import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.processor.DataUnit
import org.apache.nifi.processor.ProcessContext
import org.apache.nifi.processor.util.put.AbstractPutEventProcessor
import org.apache.nifi.ssl.SSLContextService
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

internal val ProcessContext.hostname: String
    get() = getProperty(AbstractPutEventProcessor.HOSTNAME).evaluateAttributeExpressions().value

internal val ProcessContext.port: Int
    get() = getProperty(AbstractPutEventProcessor.PORT).evaluateAttributeExpressions().asInteger()

internal fun ProcessContext.attributesToIncludeRegexOrNull(flowFile: FlowFile): Regex? {
    val property = getProperty(PutFlowToTCP.ATTRIBUTES_REGEX)

    return if (property.isSet) {
        Pattern.compile(property.evaluateAttributeExpressions(flowFile).value).toRegex()
    } else null
}

internal fun ProcessContext.attributesToIncludeSet(flowFile: FlowFile): Set<String> {
    val property = getProperty(PutFlowToTCP.ATTRIBUTES_LIST)

    return if(property.isSet) {
        property.evaluateAttributeExpressions(flowFile).value
            .split(',')
            .map { it.trim() }
            .toSet()
    } else emptySet()
}

internal val ProcessContext.includeCoreAttributes: Boolean
    get() = getProperty(PutFlowToTCP.INCLUDE_CORE_ATTRIBUTES).asBoolean()

internal val ProcessContext.missingAttributeValue: String?
    get() = if (getProperty(PutFlowToTCP.NULL_VALUE_FOR_EMPTY_STRING).asBoolean()) null else ""

internal val ProcessContext.sslContextService: SSLContextService?
    get() {
        val property = getProperty(AbstractPutEventProcessor.SSL_CONTEXT_SERVICE)

        return if (property.isSet) {
            property.asControllerService(SSLContextService::class.java)
        } else null
    }

internal val ProcessContext.separateConnectionPerFlowFile: Boolean
    get() = getProperty(PutFlowToTCP.CONNECTION_PER_FLOWFILE).asBoolean()

internal val ProcessContext.timeout: Duration
    get() = Duration.ofMillis(
        getProperty(AbstractPutEventProcessor.TIMEOUT).evaluateAttributeExpressions().asTimePeriod(TimeUnit.MILLISECONDS)
    )
internal val ProcessContext.maxSocketSendBufferSize: Int
    get() = getProperty(AbstractPutEventProcessor.MAX_SOCKET_SEND_BUFFER_SIZE).asDataSize(DataUnit.B).toInt()