package net.nerdfunk.nifi.flow.transport.netty;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;

/**
 * Flow Loop Group Factory for standardized instance creation
 */
class FlowLoopGroupFactory {
    private static final String DEFAULT_THREAD_NAME_PREFIX = "NettyFlowLoopGroup";

    private static final boolean DAEMON_THREAD_ENABLED = true;

    private String threadNamePrefix = DEFAULT_THREAD_NAME_PREFIX;

    private int workerThreads;

    /**
     * Set Thread Name Prefix used in Netty NioEventLoopGroup defaults to NettyChannel
     *
     * @param threadNamePrefix Thread Name Prefix
     */
    public void setThreadNamePrefix(final String threadNamePrefix) {
        this.threadNamePrefix = Objects.requireNonNull(threadNamePrefix, "Thread Name Prefix required");
    }

    /**
     * Set Worker Threads used in Netty NioEventLoopGroup with 0 interpreted as the default based on available processors
     *
     * @param workerThreads NioEventLoopGroup Worker Threads
     */
    public void setWorkerThreads(final int workerThreads) {
        this.workerThreads = workerThreads;
    }

    protected EventLoopGroup getFlowLoopGroup() {
        return new NioEventLoopGroup(workerThreads, getThreadFactory());
    }

    private ThreadFactory getThreadFactory() {
        return new DefaultThreadFactory(threadNamePrefix, DAEMON_THREAD_ENABLED);
    }
}
