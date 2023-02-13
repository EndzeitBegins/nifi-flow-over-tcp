package io.github.endzeitbegins.nifi.flowovertcp.testing.utils.strikt

import io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile.TestFlowFile
import io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile.toTestFlowFile
import io.github.endzeitbegins.nifi.flowovertcp.testing.tcp.TestTcpServer

internal val TestTcpServer.transferredFlowFiles: List<TestFlowFile>
    get() = receivedBytes.values.map { it.toTestFlowFile() }