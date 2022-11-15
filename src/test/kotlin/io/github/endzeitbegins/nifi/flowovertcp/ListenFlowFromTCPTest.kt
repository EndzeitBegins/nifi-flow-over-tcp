package io.github.endzeitbegins.nifi.flowovertcp

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile.TestFlowFile
import io.github.endzeitbegins.nifi.flowovertcp.testing.tcp.testTcpClient
import io.github.endzeitbegins.nifi.flowovertcp.testing.testrunner.newTestRunner
import io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile.toByteRepresentation
import io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile.toTestFlowFile
import io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile.withoutCoreAttributes
import net.nerdfunk.nifi.processors.ListenTCP2flow
import org.junit.jupiter.api.Test


class ListenFlowFromTCPTest {

    private val testRunner = newTestRunner<ListenTCP2flow>()

    @Test
    fun `supports receiving of FlowFiles with no attributes and empty content`() {
        val flowFile = TestFlowFile(
            attributes = emptyMap(),
            content = emptyList()
        )
        val hostname = "127.0.0.1"
        val port = 6666


        // start up tcp server
        testRunner.run(1, false, true)

        val tcpClient = testTcpClient(hostname, port)
        tcpClient.send(flowFile.toByteRepresentation())

        // ensure got time to read tcp data ..
        testRunner.run(1, false, false)


        testRunner.assertAllFlowFilesTransferred(ListenTCP2flow.RELATIONSHIP_SUCCESS, 1)
        val flowFiles = testRunner.getFlowFilesForRelationship(ListenTCP2flow.RELATIONSHIP_SUCCESS)
        val testFlowFile = flowFiles.single().toTestFlowFile()
        assertThat(testFlowFile.content, equalTo(emptyList()))
        assertThat(testFlowFile.withoutCoreAttributes().attributes, equalTo(emptyMap()))
    }
}
