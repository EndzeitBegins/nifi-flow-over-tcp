package io.github.endzeitbegins.nifi.flowovertcp

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasElement
import com.natpryce.hamkrest.hasSize
import io.github.endzeitbegins.nifi.flowovertcp.testing.TestFlowFile
import io.github.endzeitbegins.nifi.flowovertcp.testing.coreAttributes
import io.github.endzeitbegins.nifi.flowovertcp.testing.testrunner.newTestRunner
import io.github.endzeitbegins.nifi.flowovertcp.testing.tcp.tcpServer
import io.github.endzeitbegins.nifi.flowovertcp.testing.testrunner.enqueue
import io.github.endzeitbegins.nifi.flowovertcp.testing.toTestFlowFile
import net.nerdfunk.nifi.processors.PutFlow2TCP
import org.junit.jupiter.api.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PutFlowToTCPTest {

    private val port = 27412

    private val testRunner = newTestRunner<PutFlow2TCP> {
        setProperty(PutFlow2TCP.PORT, "$port")
    }

    private val tcpServer = tcpServer()

    @BeforeAll
    fun startTcpServer() {
        tcpServer.start(port)
    }

    @BeforeEach
    internal fun setUp() {
        testRunner.clearTransferState()
        tcpServer.clearCache()

        testRunner.setProperty(PutFlow2TCP.INCLUDE_CORE_ATTRIBUTES, "false")
    }

    @AfterAll
    fun shutdownTcpServer() {
        tcpServer.shutdown()
    }

    @Test
    fun `supports transfer of FlowFiles with no attributes and empty content`() {
        val flowFile = TestFlowFile(
            attributes = emptyMap(),
            content = emptyList()
        )

        testRunner.enqueue(flowFile)
        testRunner.run()

        testRunner.assertAllFlowFilesTransferred(PutFlow2TCP.REL_SUCCESS, 1)
        val transferredFlowFiles = tcpServer.receivedBytes.values.map { it.toTestFlowFile() }
        assertThat(transferredFlowFiles, hasSize(equalTo(1)))
        assertThat(transferredFlowFiles[0], equalTo(flowFile))
    }

    @Test
    fun `supports transfer of FlowFiles with core attributes and empty content`() {
        val flowFile = TestFlowFile(
            attributes = emptyMap(),
            content = emptyList()
        )
        testRunner.setProperty(PutFlow2TCP.INCLUDE_CORE_ATTRIBUTES, "true")

        testRunner.enqueue(flowFile)
        testRunner.run()

        testRunner.assertAllFlowFilesTransferred(PutFlow2TCP.REL_SUCCESS, 1)
        val transferredFlowFiles = tcpServer.receivedBytes.values.map { it.toTestFlowFile() }
        assertThat(transferredFlowFiles, hasSize(equalTo(1)))
        assertAll(transferredFlowFiles[0].attributes.keys.map { attributeKey ->
            { assertThat(coreAttributes, hasElement(attributeKey)) }
        })
    }

    /*
        TODO
         - no attributes, no content
         - only attributes
         - only content
         - no receiver
         - large file
         - large amount?
         - with / without core attributes
     */


}
