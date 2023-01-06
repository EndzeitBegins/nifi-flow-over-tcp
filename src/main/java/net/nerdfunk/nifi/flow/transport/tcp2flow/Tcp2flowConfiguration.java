package net.nerdfunk.nifi.flow.transport.tcp2flow;

import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.ProcessSessionFactory;
import org.apache.nifi.processor.Relationship;

import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class Tcp2flowConfiguration {

    private final boolean addIpAndPort;
    private final Relationship relationship_success;
    private final ComponentLog logger;

    private final CountDownLatch sessionFactorySetSignal;
    private AtomicReference<ProcessSessionFactory> sessionFactory;

    /**
     * constructor
     *
     * @param relationship_success Relationship Success
     * @param logger Logger
     */
    public Tcp2flowConfiguration(
            boolean addIpAndPort,
            Relationship relationship_success,
            ComponentLog logger) {
        this.addIpAndPort = addIpAndPort;
        this.relationship_success = relationship_success;
        this.logger = logger;

        this.sessionFactorySetSignal = new CountDownLatch(1);
        this.sessionFactory = new AtomicReference<>();
        this.sessionFactory.set(null);
    }

    /**
     * sets sessionFactorySetSignal
     */
    public void sessionFactorySetSignalCountDown() {
        sessionFactorySetSignal.countDown();
    }

    /**
     * returns the CountDownLatch
     *
     * @return CountDownLatch
     */
    public CountDownLatch getSessionFactorySetSignal() {
        return sessionFactorySetSignal;
    }

    /**
     * sets session factory
     * 
     * @param sessionFactory sessionFactory
     */
    public void setSessionFactory(AtomicReference<ProcessSessionFactory> sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    /**
     * returns the session factory with compareAndSet
     *
     * @param expect ProcessSessionFactory
     * @param update ProcessSessionFactory
     * @return boolean
     */
    public boolean sessionFactoryCompareAndSet(ProcessSessionFactory expect, ProcessSessionFactory update) {
        return sessionFactory.compareAndSet(expect, update);
    }

    /**
     * returns the sessionFactory
     *
     * @return AtomicReference
     */
    public AtomicReference<ProcessSessionFactory> getProcessSessionFactory() {
        return sessionFactory;
    }

    /**
     * returns the SUCCESS relationship
     *
     * @return Relationship
     */
    public Relationship getRelationshipSuccess() {
        return relationship_success;
    }

    /**
     * returns the logger object
     *
     * @return ComponentLog
     */
    public ComponentLog getLogger() {
        return logger;
    }


    /**
     * returns the addIpAndPort
     *
     * @return integer
     */
    public boolean getAddIpAndPort() {
        return addIpAndPort;
    }
}
