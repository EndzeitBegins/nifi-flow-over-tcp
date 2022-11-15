package io.github.endzeitbegins.nifi.flowovertcp

import net.nerdfunk.nifi.flow.transport.tcp2flow.Tcp2flow
import net.nerdfunk.nifi.flow.transport.tcp2flow.Tcp2flowConfiguration
import org.apache.nifi.annotation.behavior.InputRequirement
import org.apache.nifi.annotation.documentation.CapabilityDescription
import org.apache.nifi.annotation.documentation.SeeAlso
import org.apache.nifi.annotation.documentation.Tags
import org.apache.nifi.annotation.lifecycle.OnScheduled
import org.apache.nifi.annotation.lifecycle.OnStopped
import org.apache.nifi.components.PropertyDescriptor
import org.apache.nifi.expression.ExpressionLanguageScope
import org.apache.nifi.processor.AbstractSessionFactoryProcessor
import org.apache.nifi.processor.ProcessContext
import org.apache.nifi.processor.ProcessSessionFactory
import org.apache.nifi.processor.Relationship
import org.apache.nifi.processor.exception.ProcessException
import org.apache.nifi.processor.util.StandardValidators
import org.apache.nifi.processor.util.listen.ListenerProperties
import org.apache.nifi.remote.io.socket.NetworkUtils
import org.apache.nifi.security.util.ClientAuth
import org.apache.nifi.ssl.RestrictedSSLContextService
import org.apache.nifi.ssl.SSLContextService
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.*

@CapabilityDescription(
    """The ListenFlowFromTCP processor listens for incoming TCP connections and creates FlowFiles from each connection. 
        |When using the PutFlowToTCP processor, the TCP stream contains the attributes and content of the original FlowFile. 
        |These attributes and the content is written to a new FlowFile."""
)
@InputRequirement(InputRequirement.Requirement.INPUT_FORBIDDEN)
@SeeAlso(PutFlowToTCP::class)
@Tags("listen", "tcp", "ingress", "flow", "content", "attribute", "diode", "tls", "ssl")
public class ListenFlowFromTCP : AbstractSessionFactoryProcessor() {

    public companion object {
        public val IPFILTERLIST: PropertyDescriptor = PropertyDescriptor.Builder()
            .name("ip-filter-list")
            .displayName("IP Filter")
            .description(
                """Allow connections only from specified addresses.Uses cidr notation to allow hosts. 
                    |If empty, all connections are allowed.""".trimMargin()
            )
            .required(false)
            .expressionLanguageSupported(ExpressionLanguageScope.VARIABLE_REGISTRY)
            .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
            .build()

        public val READER_IDLE_TIME: PropertyDescriptor = PropertyDescriptor.Builder()
            .name("Reader idle Timer")
            .description("The amount of time in seconds a connection should be held open without being read.")
            .required(true)
            .defaultValue("5")
            .expressionLanguageSupported(ExpressionLanguageScope.VARIABLE_REGISTRY)
            .addValidator(StandardValidators.INTEGER_VALIDATOR)
            .build()

        public val SSL_CONTEXT_SERVICE: PropertyDescriptor = PropertyDescriptor.Builder()
            .name("SSL Context Service")
            .description(
                """The Controller Service to use in order to obtain an SSL Context. 
                    |If this property is set, messages will be received over a secure connection.""".trimMargin()
            )
            .required(false)
            .identifiesControllerService(RestrictedSSLContextService::class.java)
            .build()

        public val CLIENT_AUTH: PropertyDescriptor = PropertyDescriptor.Builder()
            .name("Client Auth")
            .displayName("Client Auth")
            .description("""The client authentication policy to use for the SSL Context. 
                |Only used if an SSL Context Service is provided.""".trimMargin())
            .required(false)
            .allowableValues(ClientAuth.values())
            .defaultValue(ClientAuth.REQUIRED.name)
            .dependsOn(SSL_CONTEXT_SERVICE)
            .build()

        public val ADD_IP_AND_PORT_TO_ATTRIBUTE: PropertyDescriptor = PropertyDescriptor.Builder()
            .name("Add IP and port")
            .description(
                """If set to true the listening IP address, the sender IP address and the listening TCP port are added to the attributes."""
            )
            .required(true)
            .defaultValue("false")
            .expressionLanguageSupported(ExpressionLanguageScope.NONE)
            .allowableValues("true", "false")
            .build()

        public val REL_SUCCESS: Relationship = Relationship.Builder()
            .name("success")
            .description("Relationship for successfully received files.")
            .build()

        public val REL_FAILURE: Relationship = Relationship.Builder()
            .name("error")
            .description("Relationship if an error occurred.")
            .build()
    }

    private val relationships: Set<Relationship> = setOf(REL_SUCCESS, REL_FAILURE)

    private val descriptors: List<PropertyDescriptor> = listOf(
        ListenerProperties.NETWORK_INTF_NAME,
        ListenerProperties.PORT,
        IPFILTERLIST,
        READER_IDLE_TIME,
        SSL_CONTEXT_SERVICE,
        CLIENT_AUTH,
        ADD_IP_AND_PORT_TO_ATTRIBUTE
    )

    override fun getRelationships(): Set<Relationship> = relationships

    override fun getSupportedPropertyDescriptors(): List<PropertyDescriptor> = descriptors


    private var tcp2flow: Tcp2flow? = null
    private var tcp2flowconfiguration: Tcp2flowConfiguration? = null

    /**
     * Triggers the processor but does nothing fancy
     *
     * @param context        the ProcessContext
     * @param sessionFactory the session Factory
     * @throws ProcessException if something went wrong
     */
    @Throws(ProcessException::class)
    override fun onTrigger(context: ProcessContext, sessionFactory: ProcessSessionFactory) {
        if (tcp2flowconfiguration!!.sessionFactoryCompareAndSet(null, sessionFactory)) {
            tcp2flowconfiguration!!.sessionFactorySetSignalCountDown()
        }
        context.yield()
    }

    /**
     * Starts the TCPServer to receive flowfiles over the network
     *
     * @param context the ProcessContext
     */
    @OnScheduled
    public fun startServer(context: ProcessContext) {
        if (tcp2flow == null) {
            val networkInterface =
                context.getProperty(ListenerProperties.NETWORK_INTF_NAME).evaluateAttributeExpressions().value
            val address: InetAddress? = NetworkUtils.getInterfaceAddress(networkInterface)
            val ipFilterList = context.getProperty(IPFILTERLIST).evaluateAttributeExpressions().value
            val port = context.getProperty(ListenerProperties.PORT).evaluateAttributeExpressions().asInteger()
            val readerIdleTimeout = context.getProperty(READER_IDLE_TIME).evaluateAttributeExpressions().asInteger()
            val sslContextService = context.getProperty(SSL_CONTEXT_SERVICE).asControllerService(
                SSLContextService::class.java
            )
            val writeIpAndPort = context.getProperty(ADD_IP_AND_PORT_TO_ATTRIBUTE).asBoolean()
            var clientAuth = ClientAuth.REQUIRED
            val clientAuthProperty = context.getProperty(CLIENT_AUTH)
            if (clientAuthProperty.isSet) {
                clientAuth = ClientAuth.valueOf(clientAuthProperty.value)
            }
            try {
                tcp2flowconfiguration = Tcp2flowConfiguration(
                    address,
                    port,
                    readerIdleTimeout,
                    ipFilterList,
                    sslContextService,
                    writeIpAndPort,
                    REL_SUCCESS,
                    REL_FAILURE,
                    logger
                )
                tcp2flow = Tcp2flow.Builder()
                    .Tcp2flowConfiguration(tcp2flowconfiguration)
                    .build()
                tcp2flow!!.start(clientAuth)
            } catch (processException: ProcessException) {
                logger.error(processException.message, processException)
                stopServer()
                throw processException
            } catch (ukh: UnknownHostException) {
                logger.error(ukh.message, ukh)
                stopServer()
            } catch (e: Exception) {
                logger.error(e.message, e)
            }
        } else {
            logger.warn("TCP server already started.")
        }
    }

    /**
     * Stops the TCPServer to receive flowfiles over the network
     */
    @OnStopped
    public fun stopServer() {
        try {
            if (tcp2flow != null && !tcp2flow!!.isStopped) {
                tcp2flow!!.stop()
            }
            tcp2flow = null
            tcp2flowconfiguration!!.setSessionFactory(null)
        } catch (e: Exception) {
            logger.error(e.message, e)
        }
    }
}