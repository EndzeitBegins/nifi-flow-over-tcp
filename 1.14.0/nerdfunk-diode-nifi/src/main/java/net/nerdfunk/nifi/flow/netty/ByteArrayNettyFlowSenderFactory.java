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
package net.nerdfunk.nifi.flow.netty;

import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import org.apache.nifi.event.transport.configuration.TransportProtocol;
import org.apache.nifi.event.transport.netty.channel.LogExceptionChannelHandler;
import org.apache.nifi.logging.ComponentLog;
import java.util.ArrayList;
import java.util.List;

/**
 * Netty Event Sender Factory for byte array messages
 */
public class ByteArrayNettyFlowSenderFactory extends NettyFlowSenderFactory<byte[]> {
    /**
     * Netty Event Sender Factory using byte array
     *
     * @param log Component Log
     * @param address Remote Address
     * @param port Remote Port Number
     * @param protocol Channel Protocol
     */
    public ByteArrayNettyFlowSenderFactory(final ComponentLog log, final String address, final int port, final TransportProtocol protocol) {
        super(address, port, protocol);
        final List<ChannelHandler> handlers = new ArrayList<>();
        handlers.add(new LogExceptionChannelHandler(log));
        handlers.add(new ByteArrayEncoder());
        setHandlerSupplier(() -> handlers);
    }
}
