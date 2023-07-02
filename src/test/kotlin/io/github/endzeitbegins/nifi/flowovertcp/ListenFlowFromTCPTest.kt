package io.github.endzeitbegins.nifi.flowovertcp

import io.github.endzeitbegins.nifi.flowovertcp.internal.codec.send.toByteRepresentation
import io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile.TestFlowFile
import io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile.toByteRepresentation
import io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile.withoutCoreAttributes
import io.github.endzeitbegins.nifi.flowovertcp.testing.tcp.testTcpClient
import io.github.endzeitbegins.nifi.flowovertcp.testing.utils.strikt.attributes
import io.github.endzeitbegins.nifi.flowovertcp.testing.utils.strikt.receivedFlowFile
import io.github.endzeitbegins.nifi.flowovertcp.testing.utils.strikt.receivedFlowFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.nifi.processor.util.listen.AbstractListenEventProcessor.REL_SUCCESS
import org.apache.nifi.processor.util.listen.ListenerProperties
import org.apache.nifi.util.TestRunners
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.hasEntry
import strikt.assertions.isEqualTo
import java.nio.ByteOrder
import kotlin.random.Random


class ListenFlowFromTCPTest {

    private val hostname = "127.0.0.1"

    private val processor = ListenFlowFromTCP()
    private val testRunner = TestRunners.newTestRunner(processor)

    init {
        // let the internal TCP server choose a free port by itself
        testRunner.setProperty(ListenerProperties.PORT, "0")
    }

    private val port by lazy {
        processor.listeningPort
    }

    private val tcpClient by lazy {
        testTcpClient(hostname, port)
    }

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
        expectThat(testRunner.receivedFlowFile).attributes().and {
            for ((key, value) in overridableCoreAttributes) {
                hasEntry(key, value)
            }
        }
    }

    @Test
    fun `supports adding attributes for listening and sending IP address as well as port`() {
        val flowFile = TestFlowFile(
            attributes = emptyMap(),
            content = emptyList(),
        )
        testRunner.setProperty(ListenFlowFromTCP.ADD_NETWORK_INFORMATION_ATTRIBUTES, "true")

        runWith(flowFile)

        testRunner.assertAllFlowFilesTransferred(REL_SUCCESS, 1)
        expectThat(testRunner.receivedFlowFile).attributes().and {
            hasEntry("tcp.sender", "127.0.0.1")
            hasEntry("tcp.receiver", "127.0.0.1")
            hasEntry("tcp.receiver_port", "$port")
        }
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
        expectThat(flowFile.size).isEqualTo(expectedByteLength.toLong())
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
        val receivedFlowFiles = testRunner.receivedFlowFiles
            .map { receivedFlowFile -> receivedFlowFile.withoutCoreAttributes() }

        expectThat(receivedFlowFiles).containsExactlyInAnyOrder(expectedFlowFiles)
    }
}
