package net.nerdfunk.nifi.flow.transport;

import io.netty.channel.Channel;

/**
 * Flow Sender
 *
 * @param <T,U> Flow Type and Message Header
 */
public interface FlowSender<T,U> extends AutoCloseable {

    /**
     * Aquires a new channel from Channel Pool
     * @return 
     */
    public Channel acquireChannel();

    /**
     * sends data and flushes channel
     * 
     * @param channel
     * @param data 
     */
    public void sendDataAndFlush(Channel channel, final T data);

    /**
     * sends data and flushes channel
     *
     * @param channel TCP Channel
     * @param m Header with Attributes
     */
    public void sendAttributesAndFlush(Channel channel, final U m);

    /**
     * realeases channel
     * 
     * @param channel 
     */
    public void realeaseChannel(Channel channel);
}
