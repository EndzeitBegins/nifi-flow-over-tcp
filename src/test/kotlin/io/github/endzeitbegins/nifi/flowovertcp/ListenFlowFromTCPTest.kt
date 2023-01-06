package io.github.endzeitbegins.nifi.flowovertcp

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasElement
import io.github.endzeitbegins.nifi.flowovertcp.internal.codec.send.toByteRepresentation
import io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile.TestFlowFile
import io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile.toByteRepresentation
import io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile.toTestFlowFile
import io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile.withoutCoreAttributes
import io.github.endzeitbegins.nifi.flowovertcp.testing.tcp.testTcpClient
import io.github.endzeitbegins.nifi.flowovertcp.testing.testrunner.newTestRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.nifi.processor.util.listen.AbstractListenEventProcessor.REL_SUCCESS
import org.apache.nifi.processor.util.listen.ListenerProperties
import org.apache.nifi.remote.io.socket.NetworkUtils
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.nio.ByteOrder
import kotlin.random.Random


class ListenFlowFromTCPTest {

    private val hostname = "127.0.0.1"
    private val port = NetworkUtils.availablePort()

    private val testRunner = newTestRunner<ListenFlowFromTCP> {
        setProperty(ListenerProperties.PORT, "$port")
    }

    private val tcpClient = testTcpClient(hostname, port)
    @Test
    fun `supports transfer of FlowFiles with no attributes and empty content`() {
        val flowFile = TestFlowFile(
            attributes = emptyMap(),
            content = emptyList()
        )

        runWith(flowFile)

        `assertThat FlowFile was received`(flowFile)
    }

    @Test
    fun `supports transfer of FlowFiles with no attributes and non-empty content`() {
        val flowFile = TestFlowFile(
            attributes = emptyMap(),
            content = "Hello test!".toByteArray().asList()
        )

        runWith(flowFile)

        `assertThat FlowFile was received`(flowFile)
    }

    @Test
    fun `supports transfer of FlowFiles with attributes and empty content`() {
        val flowFile = TestFlowFile(
            attributes = mapOf(
                "foo" to "bar",
                "nyan" to "cat",
            ),
            content = emptyList(),
        )

        runWith(flowFile)

        `assertThat FlowFile was received`(flowFile)
    }

    @Test
    fun `supports transfer of FlowFiles with core attributes`() {
        val overridableCoreAttributes = mapOf(
            "path" to "/a/path",
            "filename" to "nyan.cat",
        )
        val flowFile = TestFlowFile(
            attributes = overridableCoreAttributes,
            content = emptyList(),
        )

        runWith(flowFile)

        testRunner.assertAllFlowFilesTransferred(REL_SUCCESS, 1)
        val receivedFlowFile = testRunner.getFlowFilesForRelationship(REL_SUCCESS).single().toTestFlowFile()
        assertThat(receivedFlowFile.attributes.filterKeys { key -> key != "uuid" }, equalTo(overridableCoreAttributes))
    }

    @Test
    fun `supports adding attributes for listening and sending IP address as well as port`() {
        val flowFile = TestFlowFile(
            attributes = emptyMap(),
            content = emptyList(),
        )
        testRunner.setProperty(ListenFlowFromTCP.ADD_IP_AND_PORT_TO_ATTRIBUTE, "true")

        runWith(flowFile)

        testRunner.assertAllFlowFilesTransferred(REL_SUCCESS, 1)
        val receivedFlowFile = testRunner.getFlowFilesForRelationship(REL_SUCCESS)
            .single().toTestFlowFile().withoutCoreAttributes()
        assertThat(receivedFlowFile.attributes, equalTo(mapOf(
            "tcp.sender" to "127.0.0.1",
            "tcp.receiver" to "127.0.0.1",
            "tcp.receiver_port" to "$port"
        )))
    }

    @Test
    fun `supports transfer of FlowFiles with large content`() {
        // the MockProcessSession used by the StandardProcessorTestRunner
        //   loads the content of every FlowFile into an ByteArray
        // thus using to large contents yields a "java.lang.OutOfMemoryError: Java heap space"
        val random = Random(42)
        val expectedByteLength = 50_000_000 // 50 mb
        val largeContent = random.nextBytes(expectedByteLength)
        val payload =
            0.toByteRepresentation(byteOrder = ByteOrder.BIG_ENDIAN) +
                    expectedByteLength.toLong().toByteRepresentation(byteOrder = ByteOrder.BIG_ENDIAN) +
                    largeContent

        testRunner.run(1, false, true)
        tcpClient.send(payload)
        testRunner.run(1, false, false)

        testRunner.assertAllFlowFilesTransferred(REL_SUCCESS, 1)
        val flowFile = testRunner.getFlowFilesForRelationship(REL_SUCCESS).single()
        assertThat(flowFile.size, equalTo(expectedByteLength.toLong()))
    }

    @Test
    fun `supports transfer of large amount of FlowFiles`() {
        val iterations = 2_500
        val expectedIndices = (0 until iterations).toSet()
        val flowFiles = expectedIndices.map { index ->
            TestFlowFile(
                attributes = mapOf("index" to "$index"),
                content = "Hello file no. $index!".toByteArray().asList()
            )
        }

        runWith(*flowFiles.toTypedArray())

        `assertThat FlowFiles were received`(*flowFiles.toTypedArray())
    }

    @Test
    fun `supports multi-threaded execution of processor`() {
        val iterations = 300
        val expectedIndices = (0 until iterations).toSet()
        val flowFiles = expectedIndices.map { index ->
            TestFlowFile(
                attributes = mapOf("index" to "$index"),
                content = "Hello file no. $index!".toByteArray().asList()
            )
        }
        testRunner.threadCount = 10

        runWith(*flowFiles.toTypedArray())

        `assertThat FlowFiles were received`(*flowFiles.toTypedArray())
    }

    @Test
    fun `supports clustered execution of processor`() {
        val iterations = 300
        val expectedIndices = (0 until iterations).toSet()
        val flowFiles = expectedIndices.map { index ->
            TestFlowFile(
                attributes = mapOf("index" to "$index"),
                content = "Hello file no. $index!".toByteArray().asList()
            )
        }
        testRunner.setClustered(true)

        runWith(*flowFiles.toTypedArray())

        `assertThat FlowFiles were received`(*flowFiles.toTypedArray())
    }

    private fun runWith(vararg testFlowFiles: TestFlowFile) {
        testRunner.run(1, false, true)

        runBlocking {
            val sendRoutines = testFlowFiles.map { testFlowFile ->
                launch(Dispatchers.IO) { tcpClient.send(testFlowFile.toByteRepresentation()) }
            }
            sendRoutines.joinAll()
        }

        testRunner.run(testFlowFiles.size, false, false)
    }

    private fun `assertThat FlowFile was received`(flowFile: TestFlowFile) {
        `assertThat FlowFiles were received`(flowFile)
    }

    private fun `assertThat FlowFiles were received`(vararg flowFiles: TestFlowFile) {
        testRunner.assertAllFlowFilesTransferred(REL_SUCCESS, flowFiles.size)

        val expectedFlowFiles = flowFiles
            .map { flowFile -> flowFile.withoutCoreAttributes() }
        val receivedFlowFiles = testRunner.getFlowFilesForRelationship(REL_SUCCESS)
            .map { mockFlowFile -> mockFlowFile.toTestFlowFile().withoutCoreAttributes() }

        assertAll(expectedFlowFiles.map { expectedFlowFile ->
            { assertThat(receivedFlowFiles, hasElement(expectedFlowFile)) }
        })
    }
}
