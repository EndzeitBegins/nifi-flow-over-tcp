package io.github.endzeitbegins.nifi.flowovertcp

import io.github.endzeitbegins.nifi.flowovertcp.internal.codec.receive.ReceivableFlowFileServerFactory
import org.apache.nifi.annotation.behavior.InputRequirement
import org.apache.nifi.annotation.behavior.WritesAttribute
import org.apache.nifi.annotation.behavior.WritesAttributes
import org.apache.nifi.annotation.documentation.CapabilityDescription
import org.apache.nifi.annotation.documentation.SeeAlso
import org.apache.nifi.annotation.documentation.Tags
import org.apache.nifi.annotation.lifecycle.OnScheduled
import org.apache.nifi.annotation.lifecycle.OnStopped
import org.apache.nifi.components.PropertyDescriptor
import org.apache.nifi.event.transport.EventServer
import org.apache.nifi.event.transport.configuration.BufferAllocator
import org.apache.nifi.event.transport.configuration.TransportProtocol
import org.apache.nifi.expression.ExpressionLanguageScope
import org.apache.nifi.processor.AbstractSessionFactoryProcessor
import org.apache.nifi.processor.ProcessContext
import org.apache.nifi.processor.ProcessSessionFactory
import org.apache.nifi.processor.Relationship
import org.apache.nifi.processor.exception.ProcessException
import org.apache.nifi.processor.util.listen.AbstractListenEventProcessor
import org.apache.nifi.processor.util.listen.ListenerProperties
import org.apache.nifi.remote.io.socket.NetworkUtils
import org.apache.nifi.security.util.ClientAuth
import org.apache.nifi.ssl.RestrictedSSLContextService
import org.apache.nifi.ssl.SSLContextService
import java.net.InetAddress
import java.net.UnknownHostException
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLContext


private const val addIpAndPortAttributesName = "Add IP and port"

@CapabilityDescription(
    """The ListenFlowFromTCP processor listens for incoming TCP connections and creates FlowFiles from each connection. 
        When using the PutFlowToTCP processor, the TCP stream contains the attributes and content of the original FlowFile. 
        These attributes and the content is written to a new FlowFile."""
)
@InputRequirement(InputRequirement.Requirement.INPUT_FORBIDDEN)
@WritesAttributes(
    WritesAttribute(
        attribute = "tcp.sender",
        description = """IP address of the sending host. Attribute is only written, when "$addIpAndPortAttributesName" is enabled."""
    ),
    WritesAttribute(
        attribute = "tcp.receiver",
        description = """IP address of the target host. Attribute is only written, when "$addIpAndPortAttributesName" is enabled."""
    ),
    WritesAttribute(
        attribute = "tcp.receiver_port",
        description = """Port on the target host. Attribute is only written, when "$addIpAndPortAttributesName" is enabled."""
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

        // todo
        public val ADD_IP_AND_PORT_TO_ATTRIBUTE: PropertyDescriptor = PropertyDescriptor.Builder()
            .name("add-attributes-ip-port")
            .displayName(addIpAndPortAttributesName)
            .description(
                """If set to true the listening IP address, the sender IP address and the listening TCP port are added to the attributes."""
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
        SSL_CONTEXT_SERVICE,
        CLIENT_AUTH,
        ADD_IP_AND_PORT_TO_ATTRIBUTE
    )

    override fun getRelationships(): Set<Relationship> = relationships

    override fun getSupportedPropertyDescriptors(): List<PropertyDescriptor> = descriptors

    @Volatile
    private var eventServer: EventServer? = null
    private val sessionFactoryReference: AtomicReference<ProcessSessionFactory> = AtomicReference()

    override fun onTrigger(context: ProcessContext, sessionFactory: ProcessSessionFactory) {
        sessionFactoryReference.set(sessionFactory)

        context.yield()
    }

    @OnScheduled
    public fun onScheduled(context: ProcessContext) {
//        if (tcp2flow == null) {
        val networkInterface =
            context.getProperty(ListenerProperties.NETWORK_INTF_NAME).evaluateAttributeExpressions().value
        val address: InetAddress? = NetworkUtils.getInterfaceAddress(networkInterface)
        val port = context.getProperty(ListenerProperties.PORT).evaluateAttributeExpressions().asInteger()
//            val sslContextService = context.getProperty(SSL_CONTEXT_SERVICE).asControllerService(
//                SSLContextService::class.java
//            )
        val writeIpAndPort = context.getProperty(ADD_IP_AND_PORT_TO_ATTRIBUTE).asBoolean()
//            var clientAuth = ClientAuth.REQUIRED
//            val clientAuthProperty = context.getProperty(CLIENT_AUTH)
//            if (clientAuthProperty.isSet) {
//                clientAuth = ClientAuth.valueOf(clientAuthProperty.value)
//            }
        try {

            val workerThreads = 2 // context.getProperty(ListenerProperties.WORKER_THREADS).asInteger()
            val bufferSize: Int = 65507
//                    context.getProperty(ListenerProperties.RECV_BUFFER_SIZE).asDataSize(DataUnit.B).intValue()
            val socketBufferSize: Int = 1_000_000
//                    context.getProperty(ListenerProperties.MAX_SOCKET_BUFFER_SIZE).asDataSize(DataUnit.B).intValue()
            val idleTimeout: Duration = Duration.ofSeconds(30)
//                    Duration.ofSeconds(context.getProperty(IDLE_CONNECTION_TIMEOUT).asTimePeriod(TimeUnit.SECONDS))

            // todo order
            val sslContextService: SSLContextService? = context.getProperty(SSL_CONTEXT_SERVICE).asControllerService(
                SSLContextService::class.java
            )

            val eventFactory = ReceivableFlowFileServerFactory(
                address = address,
                port = port,
                protocol = TransportProtocol.TCP,

                addNetworkInformationAttributes = writeIpAndPort,
                processSessionFactoryReference = sessionFactoryReference,
                targetRelationship = AbstractListenEventProcessor.REL_SUCCESS,

                logger = logger,
            )

            // todo order
            if (sslContextService != null) {
                val clientAuthValue = context.getProperty(CLIENT_AUTH).value
                val clientAuth = ClientAuth.valueOf(clientAuthValue)
                val sslContext: SSLContext = sslContextService.createContext()
                eventFactory.setSslContext(sslContext)
                eventFactory.setClientAuth(clientAuth)
            }


            val poolReceiveBuffers: Boolean = true // todo context.getProperty(POOL_RECV_BUFFERS).asBoolean()
            val bufferAllocator: BufferAllocator =
                if (poolReceiveBuffers) BufferAllocator.POOLED else BufferAllocator.UNPOOLED
            eventFactory.setBufferAllocator(bufferAllocator)
            eventFactory.setIdleTimeout(idleTimeout)
            eventFactory.setSocketReceiveBuffer(socketBufferSize)
            eventFactory.setWorkerThreads(workerThreads)
            eventFactory.setThreadNamePrefix(String.format("%s[%s]", javaClass.simpleName, identifier))




            eventServer = eventFactory.eventServer


//                tcp2flow = Tcp2flow.Builder()
//                    .Tcp2flowConfiguration(tcp2flowconfiguration)
//                    .build()
//                tcp2flow!!.start(clientAuth)
        } catch (processException: ProcessException) {
            logger.error(processException.message, processException)
            stopped()
            throw processException
        } catch (ukh: UnknownHostException) {
            logger.error(ukh.message, ukh)
            stopped()
        } catch (e: Exception) {
            logger.error(e.message, e)
        }
    }
//    else {
//            logger.warn("TCP server already started.")
//        }
//    }

    @OnStopped
    public fun stopped() {
        eventServer?.shutdown()
        eventServer = null

        sessionFactoryReference.set(null)
    }
}