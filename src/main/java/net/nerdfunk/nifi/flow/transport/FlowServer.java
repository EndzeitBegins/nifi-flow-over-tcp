package net.nerdfunk.nifi.flow.transport;

/**
 * Event Server
 *
 */
public interface FlowServer {
    /**
     * Shutdown Event Server and close resources
     */
    void shutdown();
}
