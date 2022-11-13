package net.nerdfunk.nifi.flow.transport.message;

/**
 * Byte Array Message with Sender
 */
public class ByteArrayMessage {
    private final byte[] message;

    private final String sender;

    public ByteArrayMessage(final byte[] message, final String sender) {
        this.message = message;
        this.sender = sender;
    }

    public byte[] getMessage() {
        return message;
    }

    public String getSender() {
        return sender;
    }
}
