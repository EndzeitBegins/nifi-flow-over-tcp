package net.nerdfunk.nifi.flow.transport;

/**
 * Event Exception indicating issues when transporting events
 */
public class FlowException extends RuntimeException {
    /**
     * Flow Exception
     *
     * @param message Message
     * @param cause Throwable cause
     */
    public FlowException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
