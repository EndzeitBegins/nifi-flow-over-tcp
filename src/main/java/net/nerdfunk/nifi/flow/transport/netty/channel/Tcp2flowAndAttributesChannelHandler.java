package net.nerdfunk.nifi.flow.transport.netty.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import net.nerdfunk.nifi.flow.transport.message.FlowMessage;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessSessionFactory;
import org.apache.nifi.processor.Relationship;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class Tcp2flowAndAttributesChannelHandler extends SimpleChannelInboundHandler<FlowMessage> {

    private final AtomicReference<ProcessSessionFactory> sessionFactory;
    private final Relationship relationshipSuccess;
    private final ComponentLog logger;
    private final boolean addIpAndPort;

    private boolean haveActiveSession = false;

    private ProcessSession processSession;
    private OutputStream flowFileOutputStream;
    private FlowFile flowFile;

    public Tcp2flowAndAttributesChannelHandler(boolean addIpAndPort,
                                               AtomicReference<ProcessSessionFactory> processSessionFactory,
                                               Relationship relationshipSuccess,
                                               ComponentLog logger) {
        super();

        this.addIpAndPort = addIpAndPort;
        this.sessionFactory = processSessionFactory;
        this.relationshipSuccess = relationshipSuccess;

        this.logger = logger;

        this.processSession = null;
        this.flowFile = null;
    }

    @Override
    public void channelActive(@NotNull ChannelHandlerContext context) {
        try {
            logger.debug("got connection from host " + ((InetSocketAddress) context.channel().remoteAddress()).getAddress().getHostAddress());
            newFlow();
        } catch (Exception e) {
            logger.error("got exception while creating new flow " + e);
        }
    }

    @Override
    public void channelInactive(@NotNull ChannelHandlerContext context) throws Exception {
        if (this.haveActiveSession) {
            this.flowFileOutputStream.close();

            sendFlow(null, context);
        }
    }

    protected void newFlow() throws Exception {
        try {
            this.processSession = createProcessSession();
            this.haveActiveSession = true;
        } catch (InterruptedException | TimeoutException exception) {
            logger.error("ProcessSession could not be acquired", exception);
            throw new Exception("File transfer failed.");
        }

        this.flowFile = processSession.create();
        this.flowFileOutputStream = processSession.write(flowFile);
    }

    protected void sendFlow(byte[] header, ChannelHandlerContext context) throws java.lang.Exception {
        try {
            // check if header is not null (includes json)
            if (header != null) {
                // set attributes of header
                final ObjectMapper mapper = new ObjectMapper();
                Map<String, String> map = new HashMap<String, String>();
                try {
                    map = mapper.readValue(new String(header), Map.class);
                } catch (IOException e) {
                    this.logger.error("could not convert json string to map");
                    processSession.rollback();
                    return;
                }

                // todo use putAllAttributes + dont add ip multiple times ..
                for (String key : map.keySet()) {
                    this.flowFile = processSession.putAttribute(this.flowFile, key, String.valueOf(map.get(key)));
                }

                // write IP address and port if user wants it
                if (this.addIpAndPort) {
                    // todo safe cast using kotlin ...
                    InetSocketAddress senderAddress = (InetSocketAddress) context.channel().remoteAddress();
                    InetSocketAddress receiverAddress = (InetSocketAddress) context.channel().localAddress();

                    // TODO use putAttrbiutes
                    this.flowFile = processSession.putAttribute(this.flowFile, "tcp.sender", senderAddress.getAddress().getHostAddress());
                    this.flowFile = processSession.putAttribute(this.flowFile, "tcp.receiver", receiverAddress.getAddress().getHostAddress());
                    this.flowFile = processSession.putAttribute(this.flowFile, "tcp.receiver_port", receiverAddress.getPort() + "");
                }
            }
            processSession.getProvenanceReporter().modifyContent(this.flowFile);
            processSession.transfer(this.flowFile, relationshipSuccess);
            processSession.commitAsync();
            this.processSession = null;
            this.haveActiveSession = false;
            logger.info("flowfile received successfully and transmitted to next processor");
        } catch (Exception exception) {
            processSession.rollback();
            logger.error("Process session error. ", exception);
            this.haveActiveSession = false;
        }
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, FlowMessage msg) throws Exception {
        /*
         * check if we have an active session
         * if not than create one
         */
        if (!this.haveActiveSession) {
            newFlow();
        }

        // if payload != null write incoming data to flow
        if (msg.getPayload() != null) {
            flowFileOutputStream.write(msg.getPayload());
        }

        if (msg.isLastMessage()) {
            // close output stream, we do not need it anymore
            this.flowFileOutputStream.close();
            sendFlow(msg.getHeader(), ctx);
        }
    }

    /**
     * createProcessSession to init a new flowfile and write content to it
     *
     * @return ProcessSession
     * @throws InterruptedException
     * @throws TimeoutException
     */
    private ProcessSession createProcessSession() throws InterruptedException, TimeoutException {
        ProcessSessionFactory processSessionFactory = getProcessSessionFactory();
        return processSessionFactory.createSession();
    }

    private ProcessSessionFactory getProcessSessionFactory() throws InterruptedException, TimeoutException {
        ProcessSessionFactory processSessionFactory = sessionFactory.get();

        while (processSessionFactory == null) {
            Thread.sleep(100);

            processSessionFactory = sessionFactory.get();

        }

        return processSessionFactory;
    }
}
