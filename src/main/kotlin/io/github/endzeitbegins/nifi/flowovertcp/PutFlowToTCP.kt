package io.github.endzeitbegins.nifi.flowovertcp

import io.github.endzeitbegins.nifi.flowovertcp.internal.attributes.AttributePredicate
import io.github.endzeitbegins.nifi.flowovertcp.internal.attributes.Attributes
import io.github.endzeitbegins.nifi.flowovertcp.internal.attributes.or
import io.github.endzeitbegins.nifi.flowovertcp.internal.put.*
import io.github.endzeitbegins.nifi.flowovertcp.internal.transmission.send.FlowSender
import io.github.endzeitbegins.nifi.flowovertcp.internal.transmission.send.createFlowSender
import org.apache.nifi.annotation.behavior.InputRequirement
import org.apache.nifi.annotation.documentation.CapabilityDescription
import org.apache.nifi.annotation.documentation.SeeAlso
import org.apache.nifi.annotation.documentation.Tags
import org.apache.nifi.annotation.lifecycle.OnScheduled
import org.apache.nifi.annotation.lifecycle.OnStopped
import org.apache.nifi.components.PropertyDescriptor
import org.apache.nifi.expression.ExpressionLanguageScope
import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.processor.AbstractProcessor
import org.apache.nifi.processor.ProcessContext
import org.apache.nifi.processor.ProcessSession
import org.apache.nifi.processor.Relationship
import org.apache.nifi.processor.util.StandardValidators
import org.apache.nifi.processor.util.put.AbstractPutEventProcessor
import org.apache.nifi.ssl.SSLContextService
import kotlin.system.measureTimeMillis

@CapabilityDescription(
    """The PutFlowToTCP processor receives a FlowFile and transmits the FlowFile's attributes and content 
        |over a TCP connection to the configured TCP server. 
        |By default, each FlowFile is transmitted over a new TCP connection. 
        |An optional "Connection Per FlowFile" parameter can be specified to change the behaviour, 
        |so that multiple FlowFiles are transmitted using the same TCP connection."""
)
@InputRequirement(InputRequirement.Requirement.INPUT_REQUIRED)
@SeeAlso(ListenFlowFromTCP::class)
@Tags("put", "tcp", "egress", "flow", "content", "attribute", "diode", "tls", "ssl")
public class PutFlowToTCP : AbstractProcessor() {

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

        public val CONNECTION_PER_FLOWFILE: PropertyDescriptor = PropertyDescriptor.Builder()
            .displayName("Connection per FlowFile")
            .name("connection-per-flowfile")
            .description("""By default, each FlowFile is transmitted over an individual connection.
                |Set this to "false" to reuse connections for FlowFile transmissions.""".trimMargin())
            .required(true)
            .defaultValue("true")
            .allowableValues("true", "false")
            .build()

        public val REL_SUCCESS: Relationship = Relationship.Builder()
            .name("success")
            .description("FlowFiles that are sent successfully to the destination are sent out this relationship.")
            .build()

        public val REL_FAILURE: Relationship = Relationship.Builder()
            .name("failure")
            .description("FlowFiles that failed to send to the destination are sent out this relationship.")
            .build()
    }

    private val relationships: Set<Relationship> = setOf(REL_SUCCESS, REL_FAILURE)

    private val descriptors: List<PropertyDescriptor> = listOf(
        AbstractPutEventProcessor.HOSTNAME,
        AbstractPutEventProcessor.PORT,

        INCLUDE_CORE_ATTRIBUTES,
        ATTRIBUTES_LIST,
        ATTRIBUTES_REGEX,
        NULL_VALUE_FOR_EMPTY_STRING,

        CONNECTION_PER_FLOWFILE,

        AbstractPutEventProcessor.MAX_SOCKET_SEND_BUFFER_SIZE,
        AbstractPutEventProcessor.TIMEOUT,
        AbstractPutEventProcessor.SSL_CONTEXT_SERVICE,
    )

    override fun getRelationships(): Set<Relationship> = relationships
    override fun getSupportedPropertyDescriptors(): List<PropertyDescriptor> = descriptors

    private var flowSender: FlowSender<*>? = null

    @OnScheduled
    public fun onScheduled(context: ProcessContext) {
        flowSender = createFlowSender(context)
    }

    @OnStopped
    public fun onStopped() {
        flowSender?.close()
    }

    override fun onTrigger(context: ProcessContext, session: ProcessSession) {
        val flowFile = session.get()
            ?: return

        val flowSender: FlowSender<*>? = flowSender

        try {
            checkNotNull(flowSender) { "FlowSender has not been properly initialized!" }

            val transmissionTimeInMs = measureTimeMillis {
                flowSender.useChannel { channel ->
                    val attributesToTransmit = flowFile.filterAttributesToTransmit(context)

                    session.read(flowFile) { contentStream ->
                        channel.sendFlow(
                            attributes = attributesToTransmit,
                            contentLength = flowFile.size,
                            content = contentStream,
                        )
                    }

                }
            }

            val transitUri = "tcp://${context.hostname}:${context.port}"
            session.provenanceReporter.send(flowFile, transitUri, transmissionTimeInMs)

            session.transfer(flowFile, REL_SUCCESS)
            session.commitAsync()
        } catch (exception: Exception) {
            session.transfer(session.penalize(flowFile), REL_FAILURE)
            session.commitAsync()
            context.yield()

            logger.error(
                "Exception while handling a process session, transferring {} to failure.",
                arrayOf<Any>(flowFile),
                exception
            )
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
                shouldBeTransmitted = shouldBeTransmitted.or(matchesRegex(attributesToIncludeRegex))
            }
            if (attributesToIncludeSet.isNotEmpty()) {
                shouldBeTransmitted = shouldBeTransmitted.or(isIn(attributesToIncludeSet))
            }
            if (includeCoreAttributes) {
                shouldBeTransmitted = shouldBeTransmitted.or(isCoreAttribute)
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

            return baseResult
        }
    }

    private fun createFlowSender(context: ProcessContext): FlowSender<*> = createFlowSender(
        hostname = context.hostname,
        port = context.port,

        separateConnectionPerFlowFile = context.separateConnectionPerFlowFile,

        maxConcurrentTasks = context.maxConcurrentTasks,
        maxSocketSendBufferSize = context.maxSocketSendBufferSize,
        timeout = context.timeout,
        sslContext = context.sslContextService?.createContext(),

        logger = logger,
        logPrefix = "${javaClass.simpleName}[$identifier]",
    )
}
