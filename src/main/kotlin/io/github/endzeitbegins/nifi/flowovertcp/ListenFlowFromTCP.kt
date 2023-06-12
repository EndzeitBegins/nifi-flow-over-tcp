package io.github.endzeitbegins.nifi.flowovertcp

import io.github.endzeitbegins.nifi.flowovertcp.internal.codec.receive.ReceivableFlowFileServerFactory
import io.github.endzeitbegins.nifi.flowovertcp.internal.listen.*
import io.github.endzeitbegins.nifi.flowovertcp.internal.listen.bufferAllocator
import io.github.endzeitbegins.nifi.flowovertcp.internal.listen.idleTimeout
import io.github.endzeitbegins.nifi.flowovertcp.internal.listen.socketBufferSize
import io.github.endzeitbegins.nifi.flowovertcp.internal.listen.workerThreads
import org.apache.nifi.annotation.behavior.InputRequirement
import org.apache.nifi.annotation.behavior.WritesAttribute
import org.apache.nifi.annotation.behavior.WritesAttributes
import org.apache.nifi.annotation.documentation.CapabilityDescription
import org.apache.nifi.annotation.documentation.SeeAlso
import org.apache.nifi.annotation.documentation.Tags
import org.apache.nifi.annotation.lifecycle.OnScheduled
import org.apache.nifi.annotation.lifecycle.OnStopped
import org.apache.nifi.components.PropertyDescriptor
import org.apache.nifi.event.transport.EventException
import org.apache.nifi.event.transport.EventServer
import org.apache.nifi.event.transport.configuration.TransportProtocol
import org.apache.nifi.event.transport.netty.NettyEventServerFactory
import org.apache.nifi.expression.ExpressionLanguageScope
import org.apache.nifi.processor.AbstractSessionFactoryProcessor
import org.apache.nifi.processor.ProcessContext
import org.apache.nifi.processor.ProcessSessionFactory
import org.apache.nifi.processor.Relationship
import org.apache.nifi.processor.util.StandardValidators
import org.apache.nifi.processor.util.listen.AbstractListenEventProcessor
import org.apache.nifi.processor.util.listen.ListenerProperties
import org.apache.nifi.security.util.ClientAuth
import org.apache.nifi.ssl.RestrictedSSLContextService
import org.apache.nifi.ssl.SSLContextService
import java.util.concurrent.atomic.AtomicReference


private const val addNetworkInformationAttributesName = "Add IP and port information to attributes"

@CapabilityDescription(
    """The ListenFlowFromTCP processor listens for incoming TCP connections and creates FlowFiles from each connection. 
        When using the PutFlowToTCP processor, the TCP stream contains the attributes and content of the original FlowFile. 
        These attributes and the content is written to a new FlowFile."""
)
@InputRequirement(InputRequirement.Requirement.INPUT_FORBIDDEN)
@WritesAttributes(
    WritesAttribute(
        attribute = "tcp.sender",
        description = """IP address of the sending host. Attribute is only written, when "$addNetworkInformationAttributesName" is enabled."""
    ),
    WritesAttribute(
        attribute = "tcp.receiver",
        description = """IP address of the target host. Attribute is only written, when "$addNetworkInformationAttributesName" is enabled."""
    ),
    WritesAttribute(
        attribute = "tcp.receiver_port",
        description = """Port on the target host. Attribute is only written, when "$addNetworkInformationAttributesName" is enabled."""
    ),
)
@SeeAlso(PutFlowToTCP::class)
@Tags("listen", "tcp", "ingress", "flow", "content", "attribute", "diode", "tls", "ssl")
public class ListenFlowFromTCP : AbstractSessionFactoryProcessor() {

    public companion object {
        public val SSL_CONTEXT_SERVICE: PropertyDescriptor = PropertyDescriptor.Builder()
            .name("SSL Context Service")
            .description(
                "The Controller Service to use in order to obtain an SSL Context. If this property is set, " +
                        "messages will be received over a secure connection."
            )
            .required(false)
            .identifiesControllerService(RestrictedSSLContextService::class.java)
            .build()

        public val CLIENT_AUTH: PropertyDescriptor = PropertyDescriptor.Builder()
            .name("Client Auth")
            .description("The client authentication policy to use for the SSL Context. Only used if an SSL Context Service is provided.")
            .required(false)
            .allowableValues(ClientAuth.values())
            .defaultValue(ClientAuth.REQUIRED.name)
            .dependsOn(SSL_CONTEXT_SERVICE)
            .build()

        public val POOL_RECV_BUFFERS: PropertyDescriptor = PropertyDescriptor.Builder()
            .name("pool-receive-buffers")
            .displayName("Pool Receive Buffers")
            .description("Enable or disable pooling of buffers that the processor uses for handling bytes received on socket connections. The framework allocates buffers as needed during processing.")
            .required(true)
            .defaultValue("True")
            .allowableValues("True", "False")
            .addValidator(StandardValidators.BOOLEAN_VALIDATOR)
            .build()

        public val IDLE_CONNECTION_TIMEOUT: PropertyDescriptor = PropertyDescriptor.Builder()
            .name("idle-timeout")
            .displayName("Idle Connection Timeout")
            .description("The amount of time a client's connection will remain open if no data is received. The default of 0 seconds will leave connections open until they are closed by the client.")
            .required(true)
            .defaultValue("0 seconds")
            .addValidator(StandardValidators.TIME_PERIOD_VALIDATOR)
            .build()

        public val ADD_NETWORK_INFORMATION_ATTRIBUTES: PropertyDescriptor = PropertyDescriptor.Builder()
            .name("add-attributes-ip-port")
            .displayName(addNetworkInformationAttributesName)
            .description(
                """If set to true the IP address and port of listener and sender are added to the attributes."""
            )
            .required(true)
            .defaultValue("false")
            .expressionLanguageSupported(ExpressionLanguageScope.NONE)
            .allowableValues("true", "false")
            .build()
    }

    private val relationships: Set<Relationship> = setOf(AbstractListenEventProcessor.REL_SUCCESS)

    private val descriptors: List<PropertyDescriptor> = listOf(
        ListenerProperties.NETWORK_INTF_NAME,
        ListenerProperties.PORT,
        ADD_NETWORK_INFORMATION_ATTRIBUTES,

        // TODO Use event passing
        //  - could be a solution to https://github.com/EndzeitBegins/nifi-flow-over-tcp/issues/15 as well
        //  ListenerProperties.MAX_MESSAGE_QUEUE_SIZE,

        ListenerProperties.MAX_SOCKET_BUFFER_SIZE,
        ListenerProperties.WORKER_THREADS,
        IDLE_CONNECTION_TIMEOUT,
        POOL_RECV_BUFFERS,
        SSL_CONTEXT_SERVICE,
        CLIENT_AUTH,
    )

    override fun getRelationships(): Set<Relationship> = relationships

    override fun getSupportedPropertyDescriptors(): List<PropertyDescriptor> = descriptors

    @Volatile
    private var eventServer: EventServer? = null
    private val sessionFactoryReference: AtomicReference<ProcessSessionFactory> = AtomicReference()

    @OnScheduled
    public fun onScheduled(context: ProcessContext) {
        val address = context.address
        val port = context.port

        // TODO Use event passing
        //  - could be a solution to https://github.com/EndzeitBegins/nifi-flow-over-tcp/issues/15 as well
//        val eventsCapacity = context.getProperty(ListenerProperties.MAX_MESSAGE_QUEUE_SIZE).asInteger()
//        events = TrackingLinkedBlockingQueue(eventsCapacity)
//        errorEvents = LinkedBlockingQueue()

        val eventFactory: NettyEventServerFactory = ReceivableFlowFileServerFactory(
            address = address,
            port = port,
            protocol = TransportProtocol.TCP,

            addNetworkInformationAttributes = context.addNetworkInformationAttributes,
            processSessionFactoryReference = sessionFactoryReference,
            targetRelationship = AbstractListenEventProcessor.REL_SUCCESS,

            logger = logger,
        )

        val sslContextService: SSLContextService? = context.sslContextServiceOrNull()
        if (sslContextService != null) {
            eventFactory.setSslContext(sslContextService.createContext())
            eventFactory.setClientAuth(context.clientAuth)
        }

        eventFactory.setBufferAllocator(context.bufferAllocator)
        eventFactory.setIdleTimeout(context.idleTimeout)
        eventFactory.setSocketReceiveBuffer(context.socketBufferSize)
        eventFactory.setWorkerThreads(context.workerThreads)
        eventFactory.setThreadNamePrefix(String.format("%s[%s]", javaClass.simpleName, identifier))

        try {
            eventServer = eventFactory.eventServer
        } catch (e: EventException) {
            logger.error("Failed to bind to [{}:{}]", address, port, e)
        }
    }


    override fun onTrigger(context: ProcessContext, sessionFactory: ProcessSessionFactory) {
        // TODO Use event passing
        //  - could be a solution to https://github.com/EndzeitBegins/nifi-flow-over-tcp/issues/15 as well

        sessionFactoryReference.set(sessionFactory)

        context.yield()
    }

    @OnStopped
    public fun stopped() {
        eventServer?.shutdown()
        eventServer = null

        sessionFactoryReference.set(null)
    }

    internal val listeningPort: Int
        get() = checkNotNull(eventServer) {
            "Cannot retrieve listeningPort because eventServer has NOT been initialized!"
        }.listeningPort
}