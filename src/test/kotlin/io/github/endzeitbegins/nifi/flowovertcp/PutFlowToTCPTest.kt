package io.github.endzeitbegins.nifi.flowovertcp

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasElement
import com.natpryce.hamkrest.hasSize
import io.github.endzeitbegins.nifi.flowovertcp.testing.TestFlowFile
import io.github.endzeitbegins.nifi.flowovertcp.testing.coreAttributes
import io.github.endzeitbegins.nifi.flowovertcp.testing.tcp.testTcpServer
import io.github.endzeitbegins.nifi.flowovertcp.testing.testrunner.enqueue
import io.github.endzeitbegins.nifi.flowovertcp.testing.testrunner.newTestRunner
import io.github.endzeitbegins.nifi.flowovertcp.testing.toTestFlowFile
import net.nerdfunk.nifi.processors.PutFlow2TCP
import org.apache.nifi.flowfile.FlowFile
import org.apache.nifi.util.MockFlowFile
import org.junit.jupiter.api.*
import java.io.InputStream
import kotlin.random.Random

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PutFlowToTCPTest {

    private val port = 27412

    private val testRunner = newTestRunner<PutFlow2TCP>()

    private val tcpServer = testTcpServer()

    @BeforeAll
    fun startTcpServer() {
        tcpServer.start(port)
    }

    @BeforeEach
    internal fun setUp() {
        testRunner.clearTransferState()
        tcpServer.clearCache()

        testRunner.threadCount = 1
        testRunner.setProperty(PutFlow2TCP.INCLUDE_CORE_ATTRIBUTES, "false")
        testRunner.setProperty(PutFlow2TCP.PORT, "$port")
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

    @Test
    fun `supports transfer of FlowFiles with no attributes and non-empty content`() {
        val flowFile = TestFlowFile(
            attributes = emptyMap(),
            content = "Hello test!".toByteArray().asList()
        )
        testRunner.enqueue(flowFile)

        testRunner.run()

        testRunner.assertAllFlowFilesTransferred(PutFlow2TCP.REL_SUCCESS, 1)
        val transferredFlowFiles = tcpServer.receivedBytes.values.map { it.toTestFlowFile() }
        assertThat(transferredFlowFiles, hasSize(equalTo(1)))
        assertThat(transferredFlowFiles[0], equalTo(flowFile))
    }

    @Test
    internal fun `moves FlowFile to failure relationship, when target server cannot be reached`() {
        val flowFile = TestFlowFile(
            attributes = mapOf("foo" to "bar"),
            content = "Hello failing test!".toByteArray().asList()
        )
        testRunner.setProperty(PutFlow2TCP.PORT, "${port + 42}")
        testRunner.enqueue(flowFile)

        testRunner.run()

        testRunner.assertAllFlowFilesTransferred(PutFlow2TCP.REL_FAILURE, 1)
    }

    @Test
    internal fun `supports transfer of FlowFiles with large content`() {
        // the MockProcessSession used by the StandardProcessorTestRunner
        //   loads the content of every FlowFile into an ByteArray
        // thus using to large contents yields a "java.lang.OutOfMemoryError: Java heap space"
        val expectedByteLength = 50_000_000L // 50 mb
        val random = Random(42)
        var bytesLeft = expectedByteLength
        val data: InputStream = object: InputStream() {
            override fun read(): Int {
                return if (bytesLeft > 0) {
                    bytesLeft -= 1

                    random.nextInt(256)
                } else -1
            }
        }
        testRunner.enqueue(data)

        testRunner.run()

        testRunner.assertAllFlowFilesTransferred(PutFlow2TCP.REL_SUCCESS, 1)
        testRunner.assertAllFlowFiles { flowFile ->
            assertThat(flowFile.size, equalTo(expectedByteLength))
        }
    }

    @Test
    internal fun `supports transfer of large amount of FlowFiles`() {
        val iterations = 2_500
        val expectedIndices = (0 until iterations).toSet()
        repeat(iterations) { index ->
            val flowFile = TestFlowFile(
                attributes = mapOf("index" to "$index"),
                content = "Hello file no. $index!".toByteArray().asList()
            )
            testRunner.enqueue(flowFile)
        }

        testRunner.run(iterations)

        testRunner.assertAllFlowFilesTransferred(PutFlow2TCP.REL_SUCCESS, iterations)
        val flowFiles = testRunner.getFlowFilesForRelationship(PutFlow2TCP.REL_SUCCESS)
        val actualIndices = flowFiles.map { it.attributes.getValue("index").toInt() }.toSet()
        assertThat(actualIndices, equalTo(expectedIndices))
    }

    @Test
    internal fun `supports multi-threaded execution of processor`() {
        val iterations = 300
        val expectedIndices = (0 until iterations).toSet()
        repeat(iterations) { index ->
            val flowFile = TestFlowFile(
                attributes = mapOf("index" to "$index"),
                content = "Hello file no. $index!".toByteArray().asList()
            )
            testRunner.enqueue(flowFile)
        }
        testRunner.threadCount = 10

        testRunner.run(iterations)

        testRunner.assertAllFlowFilesTransferred(PutFlow2TCP.REL_SUCCESS, iterations)
        val flowFiles = testRunner.getFlowFilesForRelationship(PutFlow2TCP.REL_SUCCESS)
        val actualIndices = flowFiles.map { it.attributes.getValue("index").toInt() }.toSet()
        assertThat(actualIndices, equalTo(expectedIndices))
    }
}
