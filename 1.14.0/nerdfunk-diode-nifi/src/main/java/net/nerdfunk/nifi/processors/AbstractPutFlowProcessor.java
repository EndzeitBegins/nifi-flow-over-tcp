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
package net.nerdfunk.nifi.processors;

import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.annotation.lifecycle.OnStopped;
import org.apache.nifi.components.AllowableValue;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.PropertyValue;
import net.nerdfunk.nifi.flow.transport.FlowSender;
import org.apache.nifi.event.transport.configuration.TransportProtocol;
import net.nerdfunk.nifi.flow.netty.ByteArrayNettyFlowSenderFactory;
import net.nerdfunk.nifi.flow.netty.NettyFlowSenderFactory;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.AbstractSessionFactoryProcessor;
import org.apache.nifi.processor.DataUnit;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.ssl.SSLContextService;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A base class for processors that send data to an external system using TCP or UDP.
 */
public abstract class AbstractPutFlowProcessor extends AbstractSessionFactoryProcessor {

    public static final PropertyDescriptor HOSTNAME = new PropertyDescriptor.Builder()
            .name("Hostname")
            .description("The ip address or hostname of the destination.")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .defaultValue("localhost")
            .required(true)
            .expressionLanguageSupported(ExpressionLanguageScope.VARIABLE_REGISTRY)
            .build();
    public static final PropertyDescriptor PORT = new PropertyDescriptor
            .Builder().name("Port")
            .description("The port on the destination.")
            .required(true)
            .addValidator(StandardValidators.PORT_VALIDATOR)
            .expressionLanguageSupported(ExpressionLanguageScope.VARIABLE_REGISTRY)
            .build();
    public static final PropertyDescriptor MAX_SOCKET_SEND_BUFFER_SIZE = new PropertyDescriptor.Builder()
            .name("Max Size of Socket Send Buffer")
            .description("The maximum size of the socket send buffer that should be used. This is a suggestion to the Operating System " +
                    "to indicate how big the socket buffer should be. If this value is set too low, the buffer may fill up before " +
                    "the data can be read, and incoming data will be dropped.")
            .addValidator(StandardValidators.DATA_SIZE_VALIDATOR)
            .defaultValue("1 MB")
            .required(true)
            .build();
    public static final PropertyDescriptor IDLE_EXPIRATION = new PropertyDescriptor
            .Builder().name("Idle Connection Expiration")
            .description("The amount of time a connection should be held open without being used before closing the connection.")
            .required(true)
            .defaultValue("5 seconds")
            .addValidator(StandardValidators.TIME_PERIOD_VALIDATOR)
            .build();

    // Putting these properties here so sub-classes don't have to redefine them, but they are
    // not added to the properties by default since not all processors may need them
    public static final AllowableValue TCP_VALUE = new AllowableValue("TCP", "TCP");
    public static final AllowableValue UDP_VALUE = new AllowableValue("UDP", "UDP");
    public static final PropertyDescriptor PROTOCOL = new PropertyDescriptor
            .Builder().name("Protocol")
            .description("The protocol for communication.")
            .required(true)
            .allowableValues(TCP_VALUE, UDP_VALUE)
            .defaultValue(TCP_VALUE.getValue())
            .build();
    public static final PropertyDescriptor MESSAGE_DELIMITER = new PropertyDescriptor.Builder()
            .name("Message Delimiter")
            .description("Specifies the delimiter to use for splitting apart multiple messages within a single FlowFile. "
                    + "If not specified, the entire content of the FlowFile will be used as a single message. "
                    + "If specified, the contents of the FlowFile will be split on this delimiter and each section "
                    + "sent as a separate message. Note that if messages are delimited and some messages for a given FlowFile "
                    + "are transferred successfully while others are not, the messages will be split into individual FlowFiles, such that those "
                    + "messages that were successfully sent are routed to the 'success' relationship while other messages are sent to the 'failure' "
                    + "relationship.")
            .required(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
    public static final PropertyDescriptor CHARSET = new PropertyDescriptor.Builder()
            .name("Character Set")
            .description("Specifies the character set of the data being sent.")
            .required(true)
            .defaultValue("UTF-8")
            .addValidator(StandardValidators.CHARACTER_SET_VALIDATOR)
            .expressionLanguageSupported(ExpressionLanguageScope.VARIABLE_REGISTRY)
            .build();
    public static final PropertyDescriptor TIMEOUT = new PropertyDescriptor.Builder()
            .name("Timeout")
            .description("The timeout for connecting to and communicating with the destination. Does not apply to UDP")
            .required(false)
            .defaultValue("10 seconds")
            .addValidator(StandardValidators.TIME_PERIOD_VALIDATOR)
            .expressionLanguageSupported(ExpressionLanguageScope.VARIABLE_REGISTRY)
            .build();
    public static final PropertyDescriptor OUTGOING_MESSAGE_DELIMITER = new PropertyDescriptor.Builder()
            .name("Outgoing Message Delimiter")
            .description("Specifies the delimiter to use when sending messages out over the same TCP stream. The delimiter is appended to each FlowFile message "
                    + "that is transmitted over the stream so that the receiver can determine when one message ends and the next message begins. Users should "
                    + "ensure that the FlowFile content does not contain the delimiter character to avoid errors. In order to use a new line character you can "
                    + "enter '\\n'. For a tab character use '\\t'. Finally for a carriage return use '\\r'.")
            .required(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .expressionLanguageSupported(ExpressionLanguageScope.FLOWFILE_ATTRIBUTES)
            .build();
    public static final PropertyDescriptor CONNECTION_PER_FLOWFILE = new PropertyDescriptor.Builder()
            .name("Connection Per FlowFile")
            .description("Specifies whether to send each FlowFile's content on an individual connection.")
            .required(true)
            .defaultValue("false")
            .allowableValues("true", "false")
            .build();
    public static final PropertyDescriptor SSL_CONTEXT_SERVICE = new PropertyDescriptor.Builder()
            .name("SSL Context Service")
            .description("The Controller Service to use in order to obtain an SSL Context. If this property is set, " +
                    "messages will be sent over a secure connection.")
            .required(false)
            .identifiesControllerService(SSLContextService.class)
            .build();

    public static final Relationship REL_SUCCESS = new Relationship.Builder()
            .name("success")
            .description("FlowFiles that are sent successfully to the destination are sent out this relationship.")
            .build();
    public static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("failure")
            .description("FlowFiles that failed to send to the destination are sent out this relationship.")
            .build();

    private Set<Relationship> relationships;
    private List<PropertyDescriptor> descriptors;

    protected volatile String transitUri;
    protected FlowSender flowSender;

    protected final BlockingQueue<FlowFileMessageBatch> completeBatches = new LinkedBlockingQueue<>();
    protected final Set<FlowFileMessageBatch> activeBatches = Collections.synchronizedSet(new HashSet<>());

    @Override
    protected void init(final ProcessorInitializationContext context) {
        final List<PropertyDescriptor> descriptors = new ArrayList<>();
        descriptors.add(HOSTNAME);
        descriptors.add(PORT);
        descriptors.add(MAX_SOCKET_SEND_BUFFER_SIZE);
        descriptors.add(IDLE_EXPIRATION);
        descriptors.add(TIMEOUT);
        descriptors.addAll(getAdditionalProperties());
        this.descriptors = Collections.unmodifiableList(descriptors);

        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_SUCCESS);
        relationships.add(REL_FAILURE);
        relationships.addAll(getAdditionalRelationships());
        this.relationships = Collections.unmodifiableSet(relationships);
    }

    /**
     * Override to provide additional relationships for the processor.
     *
     * @return a list of relationships
     */
    protected List<Relationship> getAdditionalRelationships() {
        return Collections.EMPTY_LIST;
    }

    /**
     * Override to provide additional properties for the processor.
     *
     * @return a list of properties
     */
    protected List<PropertyDescriptor> getAdditionalProperties() {
        return Collections.EMPTY_LIST;
    }

    @Override
    public final Set<Relationship> getRelationships() {
        return this.relationships;
    }

    @Override
    public final List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return descriptors;
    }

    @OnScheduled
    public void onScheduled(final ProcessContext context) throws IOException {
        // initialize the queue of senders, one per task, senders will get created on the fly in onTrigger
        this.flowSender = getFlowSender(context);
        this.transitUri = createTransitUri(context);
    }

    @OnStopped
    public void closeSenders() throws Exception {
        if (flowSender != null) {
            flowSender.close();
        }
    }

    /**
     * Sub-classes construct a transit uri for provenance events. Called from @OnScheduled
     * method of this class.
     *
     * @param context the current context
     *
     * @return the transit uri
     */
    protected abstract String createTransitUri(final ProcessContext context);

    protected FlowSender<?> getFlowSender(final ProcessContext context) {
        final String hostname = context.getProperty(HOSTNAME).evaluateAttributeExpressions().getValue();
        final int port = context.getProperty(PORT).evaluateAttributeExpressions().asInteger();
        final String protocol = getProtocol(context);
        final boolean singleEventPerConnection = context.getProperty(CONNECTION_PER_FLOWFILE).getValue() != null ? context.getProperty(CONNECTION_PER_FLOWFILE).asBoolean() : false;

        final NettyFlowSenderFactory factory = getNettyFlowSenderFactory(hostname, port, protocol);
        factory.setThreadNamePrefix(String.format("%s[%s]", getClass().getSimpleName(), getIdentifier()));
        factory.setWorkerThreads(context.getMaxConcurrentTasks());
        factory.setMaxConnections(context.getMaxConcurrentTasks());
        factory.setSocketSendBufferSize(context.getProperty(MAX_SOCKET_SEND_BUFFER_SIZE).asDataSize(DataUnit.B).intValue());
        factory.setSingleEventPerConnection(singleEventPerConnection);

        final int timeout = context.getProperty(TIMEOUT).evaluateAttributeExpressions().asTimePeriod(TimeUnit.MILLISECONDS).intValue();
        factory.setTimeout(Duration.ofMillis(timeout));

        final PropertyValue sslContextServiceProperty = context.getProperty(SSL_CONTEXT_SERVICE);
        if (sslContextServiceProperty.isSet()) {
            final SSLContextService sslContextService = sslContextServiceProperty.asControllerService(SSLContextService.class);
            final SSLContext sslContext = sslContextService.createContext();
            factory.setSslContext(sslContext);
        }

        return factory.getFlowSender();
    }

    /**
     * The results from pruning connections.
     */
    protected static class PruneResult {

        private final int numClosed;

        private final int numConsidered;

        public PruneResult(final int numClosed, final int numConsidered) {
            this.numClosed = numClosed;
            this.numConsidered = numConsidered;
        }

        public int getNumClosed() {
            return numClosed;
        }

        public int getNumConsidered() {
            return numConsidered;
        }

    }


    /**
     * Represents a range of messages from a FlowFile.
     */
    protected static class Range {
        private final long start;
        private final long end;

        public Range(final long start, final long end) {
            this.start = start;
            this.end = end;
        }

        public long getStart() {
            return start;
        }

        public long getEnd() {
            return end;
        }

        @Override
        public String toString() {
            return "Range[" + start + "-" + end + "]";
        }
    }

    /**
     * A wrapper to hold the ranges of a FlowFile that were successful and ranges that failed, and then
     * transfer those ranges appropriately.
     */
    protected class FlowFileMessageBatch {

        private final ProcessSession session;
        private final FlowFile flowFile;
        private final long startTime = System.nanoTime();

        private final List<Range> successfulRanges = new ArrayList<>();
        private final List<Range> failedRanges = new ArrayList<>();

        private Exception lastFailureReason;
        private long numMessages = -1L;
        private long completeTime = 0L;
        private boolean canceled = false;

        public FlowFileMessageBatch(final ProcessSession session, final FlowFile flowFile) {
            this.session = session;
            this.flowFile = flowFile;
        }

        public synchronized void cancelOrComplete() {
            if (isComplete()) {
                completeSession();
                return;
            }

            this.canceled = true;

            session.rollback();
            successfulRanges.clear();
            failedRanges.clear();
        }

        public synchronized void addSuccessfulRange(final long start, final long end) {
            if (canceled) {
                return;
            }

            successfulRanges.add(new Range(start, end));

            if (isComplete()) {
                activeBatches.remove(this);
                completeBatches.add(this);
                completeTime = System.nanoTime();
            }
        }

        public synchronized void addFailedRange(final long start, final long end, final Exception e) {
            if (canceled) {
                return;
            }

            failedRanges.add(new Range(start, end));
            lastFailureReason = e;

            if (isComplete()) {
                activeBatches.remove(this);
                completeBatches.add(this);
                completeTime = System.nanoTime();
            }
        }

        private boolean isComplete() {
            return !canceled && (numMessages > -1) && (successfulRanges.size() + failedRanges.size() >= numMessages);
        }

        public synchronized void setNumMessages(final long msgCount) {
            this.numMessages = msgCount;

            if (isComplete()) {
                activeBatches.remove(this);
                completeBatches.add(this);
                completeTime = System.nanoTime();
            }
        }

        private void transferRanges(final List<Range> ranges, final Relationship relationship) {
            Collections.sort(ranges, new Comparator<Range>() {
                @Override
                public int compare(final Range o1, final Range o2) {
                    return Long.compare(o1.getStart(), o2.getStart());
                }
            });

            for (int i = 0; i < ranges.size(); i++) {
                Range range = ranges.get(i);
                int count = 1;

                while (i + 1 < ranges.size()) {
                    // Check if the next range in the List continues where this one left off.
                    final Range nextRange = ranges.get(i + 1);

                    if (nextRange.getStart() == range.getEnd()) {
                        // We have two ranges in a row that are contiguous; combine them into a single Range.
                        range = new Range(range.getStart(), nextRange.getEnd());

                        count++;
                        i++;
                    } else {
                        break;
                    }
                }

                // Create a FlowFile for this range.
                FlowFile child = session.clone(flowFile, range.getStart(), range.getEnd() - range.getStart());
                if (relationship == REL_SUCCESS) {
                    session.getProvenanceReporter().send(child, transitUri, "Sent " + count + " messages");
                    session.transfer(child, relationship);
                } else {
                    child = session.penalize(child);
                    session.transfer(child, relationship);
                }
            }
        }

        public synchronized void completeSession() {
            if (canceled) {
                return;
            }

            if (successfulRanges.isEmpty() && failedRanges.isEmpty()) {
                getLogger().info("Completed processing {} but sent 0 FlowFiles", new Object[] {flowFile});
                session.transfer(flowFile, REL_SUCCESS);
                session.commitAsync();
                return;
            }

            if (successfulRanges.isEmpty()) {
                getLogger().error("Failed to send {}; routing to 'failure'; last failure reason reported was {};", new Object[] {flowFile, lastFailureReason});
                final FlowFile penalizedFlowFile = session.penalize(flowFile);
                session.transfer(penalizedFlowFile, REL_FAILURE);
                session.commitAsync();
                return;
            }

            if (failedRanges.isEmpty()) {
                final long transferMillis = TimeUnit.NANOSECONDS.toMillis(completeTime - startTime);
                session.getProvenanceReporter().send(flowFile, transitUri, "Sent " + successfulRanges.size() + " messages;", transferMillis);
                session.transfer(flowFile, REL_SUCCESS);
                getLogger().info("Successfully sent {} messages for {} in {} millis", new Object[] {successfulRanges.size(), flowFile, transferMillis});
                session.commitAsync();
                return;
            }

            // At this point, the successful ranges is not empty and the failed ranges is not empty. This indicates that some messages made their way
            // successfully and some failed. We will address this by splitting apart the source FlowFile into children and sending the successful messages to 'success'
            // and the failed messages to 'failure'.
            transferRanges(successfulRanges, REL_SUCCESS);
            transferRanges(failedRanges, REL_FAILURE);
            session.remove(flowFile);
            getLogger().error("Successfully sent {} messages, but failed to send {} messages; the last error received was {}",
                    new Object[] {successfulRanges.size(), failedRanges.size(), lastFailureReason});
            session.commitAsync();
        }
    }

    /**
     * Gets the current value of the "Outgoing Message Delimiter" property and parses the special characters.
     *
     * @param context
     *            - the current process context.
     * @param flowFile
     *            - the FlowFile being processed.
     *
     * @return String containing the Delimiter value.
     */
    protected String getOutgoingMessageDelimiter(final ProcessContext context, final FlowFile flowFile) {
        String delimiter = context.getProperty(OUTGOING_MESSAGE_DELIMITER).evaluateAttributeExpressions(flowFile).getValue();
        if (delimiter != null) {
            delimiter = delimiter.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
        }
        return delimiter;
    }

    protected String getProtocol(final ProcessContext context) {
        return context.getProperty(PROTOCOL).getValue();
    }

    protected NettyFlowSenderFactory<?> getNettyFlowSenderFactory(final String hostname, final int port, final String protocol) {
        return new ByteArrayNettyFlowSenderFactory(getLogger(), hostname, port, TransportProtocol.valueOf(protocol));
    }
}
