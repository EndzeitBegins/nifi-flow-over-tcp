/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.endzeitbegins.nifi.flowovertcp

import io.github.endzeitbegins.nifi.flowovertcp.internal.attributes.AttributePredicate
import io.github.endzeitbegins.nifi.flowovertcp.internal.attributes.Attributes
import io.github.endzeitbegins.nifi.flowovertcp.internal.attributes.or
import io.github.endzeitbegins.nifi.flowovertcp.internal.objectMapper
import io.github.endzeitbegins.nifi.flowovertcp.internal.put.*
import io.netty.channel.Channel
import net.nerdfunk.nifi.flow.transport.FlowSender
import net.nerdfunk.nifi.flow.transport.message.FlowMessage
import net.nerdfunk.nifi.flow.transport.netty.NettyFlowAndAttributesSenderFactory
import net.nerdfunk.nifi.processors.ListenTCP2flow
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
import org.apache.nifi.ssl.SSLContextService
import java.io.InputStream
import kotlin.system.measureTimeMillis

@CapabilityDescription(
    """The PutFlowToTCP processor receives a FlowFile and transmits the FlowFile's attributes and content 
        |over a TCP connection to the configured TCP server. 
        |By default, each FlowFile is transmitted over a new TCP connection. 
        |An optional "Connection Per FlowFile" parameter can be specified to change the behaviour, 
        |so that multiple FlowFiles are transmitted using the same TCP connection."""
)
@InputRequirement(InputRequirement.Requirement.INPUT_REQUIRED)
@SeeAlso(ListenTCP2flow::class)
@Tags("remote", "egress", "put", "tcp", "flow", "content    ", "attribute")
public class PutFlowToTCP : AbstractProcessor() {

    public companion object {

        public val HOSTNAME: PropertyDescriptor = PropertyDescriptor.Builder()
            .name("hostname")
            .displayName("Hostname")
            .description("The ip address or hostname of the destination.")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .defaultValue("localhost")
            .required(true)
            .expressionLanguageSupported(ExpressionLanguageScope.VARIABLE_REGISTRY)
            .build()

        public val PORT: PropertyDescriptor = PropertyDescriptor.Builder()
            .name("port")
            .displayName("Port")
            .description("The port on the destination.")
            .required(true)
            .addValidator(StandardValidators.PORT_VALIDATOR)
            .expressionLanguageSupported(ExpressionLanguageScope.VARIABLE_REGISTRY)
            .build()

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

        public val MAX_SOCKET_SEND_BUFFER_SIZE: PropertyDescriptor = PropertyDescriptor.Builder()
            .name("Max Size of Socket Send Buffer")
            .description(
                "The maximum size of the socket send buffer that should be used. This is a suggestion to the Operating System " +
                        "to indicate how big the socket buffer should be. If this value is set too low, the buffer may fill up before " +
                        "the data can be read, and incoming data will be dropped."
            )
            .addValidator(StandardValidators.DATA_SIZE_VALIDATOR)
            .defaultValue("1 MB")
            .required(true)
            .build()

        public val TIMEOUT: PropertyDescriptor = PropertyDescriptor.Builder()
            .name("Timeout")
            .description("The timeout for connecting to and communicating with the destination")
            .required(false)
            .defaultValue("10 seconds")
            .addValidator(StandardValidators.TIME_PERIOD_VALIDATOR)
            .expressionLanguageSupported(ExpressionLanguageScope.VARIABLE_REGISTRY)
            .build()

        public val SSL_CONTEXT_SERVICE: PropertyDescriptor = PropertyDescriptor.Builder()
            .name("SSL Context Service")
            .description(
                "The Controller Service to use in order to obtain an SSL Context. If this property is set, " +
                        "messages will be sent over a secure connection."
            )
            .required(false)
            .identifiesControllerService(SSLContextService::class.java)
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
        HOSTNAME,
        PORT,

        INCLUDE_CORE_ATTRIBUTES,
        ATTRIBUTES_LIST,
        ATTRIBUTES_REGEX,
        NULL_VALUE_FOR_EMPTY_STRING,

        CONNECTION_PER_FLOWFILE,

        MAX_SOCKET_SEND_BUFFER_SIZE,
        TIMEOUT,
        SSL_CONTEXT_SERVICE,
    )

    override fun getRelationships(): Set<Relationship> = relationships
    override fun getSupportedPropertyDescriptors(): List<PropertyDescriptor> = descriptors

    private var flowSenderField: FlowSender<InputStream, FlowMessage>? = null

    @OnScheduled
    public fun onScheduled(context: ProcessContext) {
        flowSenderField = getFlowSender(context)
    }

    @OnStopped
    public fun onStopped() {
        flowSenderField?.close()
    }

    override fun onTrigger(context: ProcessContext, session: ProcessSession) {
        val flowFile = session.get() ?: return

        val flowSender: FlowSender<InputStream, FlowMessage>? = flowSenderField
        var channel: Channel? = null

        try {
            checkNotNull(flowSender) { "FlowSender has not been properly initialized!" }
            channel = flowSender.acquireChannel()

            val transmissionTimeInMs = measureTimeMillis {
                val attributesToTransmit = flowFile.filterAttributesToTransmit(context)

                flowSender.sendHeaderAndFlowFileAttributes(
                    channel = channel,
                    attributesToTransmit = attributesToTransmit,
                    payloadLength = flowFile.size
                )

                flowSender.sendFlowFileContent(channel = channel, session = session, flowFile = flowFile)
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
        } finally {
            if (flowSender != null && channel != null) {
                flowSender.realeaseChannel(channel)
            }
        }
    }

    private fun FlowSender<InputStream, FlowMessage>.sendHeaderAndFlowFileAttributes(
        channel: Channel,
        attributesToTransmit: Attributes,
        payloadLength: Long
    ) {
        val attributesAsBytes = objectMapper.writeValueAsBytes(attributesToTransmit)

        val headerLength = attributesAsBytes.size

        val header = FlowMessage().apply {
            this.headerlength = headerLength
            this.payloadlength = payloadLength
            this.header = attributesAsBytes
        }

        logger.debug("Sending header with headerLength=$headerLength and payloadLength=$payloadLength and attributes...")
        sendAttributesAndFlush(channel, header)
        logger.debug("Sent header and attributes.")
    }

    private fun FlowSender<InputStream, FlowMessage>.sendFlowFileContent(
        channel: Channel,
        session: ProcessSession,
        flowFile: FlowFile
    ) {
        val flowFileContentSize = flowFile.size

        logger.debug("Sending FlowFile content of $flowFileContentSize bytes...")
        session.read(flowFile) { contentStream -> sendDataAndFlush(channel, contentStream) }
        logger.debug("FlowFile content of $flowFileContentSize bytes sent.")
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

    private fun getFlowSender(context: ProcessContext): FlowSender<InputStream, FlowMessage> {
        val factory = NettyFlowAndAttributesSenderFactory(logger, context.hostname, context.port)
        factory.setThreadNamePrefix(String.format("%s[%s]", javaClass.simpleName, identifier))
        factory.setWorkerThreads(context.maxConcurrentTasks)
        factory.setMaxConnections(context.maxConcurrentTasks)
        factory.setSocketSendBufferSize(context.maxSocketSendBufferSize)
        factory.setSingleFlowPerConnection(context.singleConnectionPerFlowFile)
        factory.setTimeout(context.timeout)

        context.sslContextService?.also { sslContextService ->
            factory.setSslContext(sslContextService.createContext())
        }

        return factory.flowSender
    }
}


