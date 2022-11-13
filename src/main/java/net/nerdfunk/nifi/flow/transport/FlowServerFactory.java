package net.nerdfunk.nifi.flow.transport;

/**
 * Flow Server Factory
 *
 */
public interface FlowServerFactory {
    /**
     * Get Event Server
     *
     * @return Flow Server
     */
    FlowServer getFlowServer();
}
