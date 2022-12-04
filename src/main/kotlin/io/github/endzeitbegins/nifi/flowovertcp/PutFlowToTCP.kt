package io.github.endzeitbegins.nifi.flowovertcp

import io.github.endzeitbegins.nifi.flowovertcp.internal.attributes.AttributePredicate
import io.github.endzeitbegins.nifi.flowovertcp.internal.attributes.Attributes
import io.github.endzeitbegins.nifi.flowovertcp.internal.attributes.or
import io.github.endzeitbegins.nifi.flowovertcp.internal.codec.send.TransmittableFlowFile
import io.github.endzeitbegins.nifi.flowovertcp.internal.codec.send.TransmittableFlowFileSenderFactory
import io.github.endzeitbegins.nifi.flowovertcp.internal.put.*
import org.apache.nifi.annotation.behavior.InputRequirement
import org.apache.nifi.annotation.documentation.CapabilityDescription
import org.apache.nifi.annotation.documentation.SeeAlso
import org.apache.nifi.annotation.documentation.Tags
import org.apache.nifi.components.PropertyDescriptor
import org.apache.nifi.event.transport.configuration.TransportProtocol
import org.apache.nifi.event.transport.netty.NettyEventSenderFactory
import org.apache.nifi.expression.ExpressionLanguageScope
import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.processor.ProcessContext
import org.apache.nifi.processor.ProcessSessionFactory
import org.apache.nifi.processor.util.StandardValidators
import org.apache.nifi.processor.util.put.AbstractPutEventProcessor
import kotlin.system.measureTimeMillis

@CapabilityDescription(
    """The PutFlowToTCP processor receives a FlowFile and transmits the FlowFile's attributes and content 
        over a TCP connection to the configured TCP server. 
        By default, each FlowFile is transmitted over a new TCP connection. 
        An optional "Connection Per FlowFile" parameter can be specified to change the behaviour, 
        so that multiple FlowFiles are transmitted using the same TCP connection."""
)
@InputRequirement(InputRequirement.Requirement.INPUT_REQUIRED)
@SeeAlso(ListenFlowFromTCP::class)
@Tags("put", "tcp", "egress", "flow", "content", "attribute", "diode", "tls", "ssl")
public class PutFlowToTCP : AbstractPutEventProcessor<TransmittableFlowFile>() {

    public companion object {
        private const val attributesListName = "Attributes List"
        private const val attributesRegexName = "Attributes Regular Expression"
        private const val nullForMissingAttributeName = """Use null for missing attributes"""

        public val INCLUDE_CORE_ATTRIBUTES: PropertyDescriptor = PropertyDescriptor.Builder()
            .name("include-core-attributes")
            .displayName("Include Core Attributes")
            .description(
                """By default, the core FlowFile attributes contained in every FlowFile are transmitted.
                    |Set this to "false" in order to exclude the org.apache.nifi.flowfile.attributes.CoreAttributes
                    |from the list of attributes to transmit. 
                    |Note that the FlowFile uuid is never transmitted, as it cannot be restored on the receiving side.  
                """.trimMargin()
            )
            .required(true)
            .allowableValues("true", "false")
            .defaultValue("true")
            .build()

        public val ATTRIBUTES_LIST: PropertyDescriptor = PropertyDescriptor.Builder()
            .name("attributes-list")
            .displayName(attributesListName)
            .description(
                """Comma separated list of FlowFile attributes to transmit.
                    |By default, or if this value is left empty, all existing attributes will be included.
                    |The attribute name matching is case sensitive.
                    |
                    |If an attribute specified in the list cannot be found in thw FlowFile,
                    |a value according to the property "$nullForMissingAttributeName" will be transmitted.
                    |
                    |This property can be used in combination with "$attributesRegexName", in which case all attributes
                    |which are either in the list or match the regular expression will be transmitted""".trimMargin()
            )
            .required(false)
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
            .build()

        public val ATTRIBUTES_REGEX: PropertyDescriptor = PropertyDescriptor.Builder()
            .name("attributes-regex")
            .displayName(attributesRegexName)
            .description(
                """Regular expression to match FlowFile attributes to transmit.
                    |By default, or if this value is left empty, all existing attributes will be included.
                    |
                    |This property can be used in combination with "$attributesListName", in which case all attributes
                    |which are either in the list or match the regular expression will be transmitted""".trimMargin()
            )
            .required(false)
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .addValidator(StandardValidators.createRegexValidator(0, Int.MAX_VALUE, true))
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build()

        public val NULL_VALUE_FOR_EMPTY_STRING: PropertyDescriptor = PropertyDescriptor.Builder()
            .name("use-null-for-missing-attribute")
            .displayName(nullForMissingAttributeName)
            .description(
                """By default, an empty string is used for attributes defined in the "$attributesListName" but missing in the FlowFile.
                    |Set this to "true" to write a value of "null" for missing attributes instead.""".trimMargin()
            )
            .required(true)
            .allowableValues("true", "false")
            .defaultValue("false")
            .build()
    }

    override fun getAdditionalProperties(): List<PropertyDescriptor> = listOf(
        INCLUDE_CORE_ATTRIBUTES,
        ATTRIBUTES_LIST,
        ATTRIBUTES_REGEX,
        NULL_VALUE_FOR_EMPTY_STRING,

        CONNECTION_PER_FLOWFILE,
        SSL_CONTEXT_SERVICE,
    )

    override fun onTrigger(context: ProcessContext, sessionFactory: ProcessSessionFactory) {
        val session = sessionFactory.createSession()
        val flowFile = session.get()
            ?: return

        try {
            val transmissionTimeInMs = measureTimeMillis {
                session.read(flowFile) { inputStream ->
                    val attributesToTransmit = flowFile.filterAttributesToTransmit(context)

                    val transmittableFlowFile = TransmittableFlowFile(
                        attributes = attributesToTransmit,
                        contentLength = flowFile.size,
                        contentStream = inputStream,
                    )

                    eventSender.sendEvent(transmittableFlowFile)
                }
            }

            session.provenanceReporter.send(flowFile, transitUri, transmissionTimeInMs)
            session.transfer(flowFile, REL_SUCCESS)
            session.commitAsync()
        } catch (e: Exception) {
            logger.error("Send Failed {}", flowFile, e)
            session.transfer(session.penalize(flowFile), REL_FAILURE)
            session.commitAsync()
            context.yield()
        }
    }

    private fun FlowFile.filterAttributesToTransmit(
        context: ProcessContext
    ): Attributes {
        val allAttributes: Attributes = attributes

        val attributesToIncludeRegex: Regex? = context.attributesToIncludeRegexOrNull(this)
        val attributesToIncludeSet: Set<String> = context.attributesToIncludeSet(this)
        val includeCoreAttributes: Boolean = context.includeCoreAttributes

        return if (attributesToIncludeRegex == null && attributesToIncludeSet.isEmpty()) {
            if (includeCoreAttributes) allAttributes else allAttributes.filterNot(isCoreAttribute)
        } else {
            var shouldBeTransmitted: AttributePredicate = { false }
            if (attributesToIncludeRegex != null) {
                shouldBeTransmitted = shouldBeTransmitted or matchesRegex(attributesToIncludeRegex)
            }
            if (attributesToIncludeSet.isNotEmpty()) {
                shouldBeTransmitted = shouldBeTransmitted or isIn(attributesToIncludeSet)
            }
            if (includeCoreAttributes) {
                shouldBeTransmitted = shouldBeTransmitted or isCoreAttribute
            }

            val baseResult = allAttributes
                .filter(shouldBeTransmitted)
                .toMutableMap()

            if (attributesToIncludeSet.isNotEmpty()) {
                val missingAttributes = attributesToIncludeSet - baseResult.keys

                val missingAttributeValue = context.missingAttributeValue
                for (missingAttribute in missingAttributes) {
                    baseResult[missingAttribute] = missingAttributeValue
                }
            }

            baseResult
        }.filterNot(isFlowFileUuid)
    }

    override fun getNettyEventSenderFactory(
        hostname: String,
        port: Int,
        protocol: String
    ): NettyEventSenderFactory<TransmittableFlowFile> =
        TransmittableFlowFileSenderFactory(hostname, port, TransportProtocol.TCP, logger)

    override fun getProtocol(context: ProcessContext): String = TCP_VALUE.value

}
