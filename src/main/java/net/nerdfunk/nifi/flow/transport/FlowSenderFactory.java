package net.nerdfunk.nifi.flow.transport;

/**
 * Flow Sender Factory
 *
 * @param <T,U> Flow Type and Message Header
 *
 */
public interface FlowSenderFactory<T,U> {
    /**
     * Get Flow Sender
     *
     * @return Flow Sender
     */
    FlowSender<T,U> getFlowSender();
}
