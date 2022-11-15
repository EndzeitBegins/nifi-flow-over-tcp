package io.github.endzeitbegins.nifi.flowovertcp

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasElement
import com.natpryce.hamkrest.hasSize
import io.github.endzeitbegins.nifi.flowovertcp.PutFlowToTCP.Companion.REL_FAILURE
import io.github.endzeitbegins.nifi.flowovertcp.PutFlowToTCP.Companion.REL_SUCCESS
import io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile.TestFlowFile
import io.github.endzeitbegins.nifi.flowovertcp.internal.attributes.coreAttributes
import io.github.endzeitbegins.nifi.flowovertcp.testing.tcp.testTcpServer
import io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile.enqueue
import io.github.endzeitbegins.nifi.flowovertcp.testing.testrunner.newTestRunner
import io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile.toTestFlowFile
import org.apache.nifi.processor.util.put.AbstractPutEventProcessor
import org.junit.jupiter.api.*
import java.io.InputStream
import kotlin.random.Random

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PutFlowToTCPTest {

    private val port = 27412

    private val testRunner = newTestRunner<PutFlowToTCP>()

    private val tcpServer = testTcpServer()

    @BeforeAll
    fun startTcpServer() {
        tcpServer.start(port)
    }

    @BeforeEach
    fun setUp() {
        testRunner.clearTransferState()
        testRunner.clearProperties()
        tcpServer.clearCache()

        testRunner.threadCount = 1
        testRunner.setClustered(false)

        testRunner.setProperty(PutFlowToTCP.INCLUDE_CORE_ATTRIBUTES, "false")
        testRunner.setProperty(AbstractPutEventProcessor.PORT, "$port")
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

        testRunner.assertAllFlowFilesTransferred(REL_SUCCESS, 1)
        val transferredFlowFiles = tcpServer.receivedBytes.values.map { it.toTestFlowFile() }
        assertThat(transferredFlowFiles, hasSize(equalTo(1)))
        assertThat(transferredFlowFiles[0], equalTo(flowFile))
    }

    @Test
    fun `supports transfer of FlowFiles with no attributes and non-empty content`() {
        val flowFile = TestFlowFile(
            attributes = emptyMap(),
            content = "Hello test!".toByteArray().asList()
        )
        testRunner.enqueue(flowFile)

        testRunner.run()

        testRunner.assertAllFlowFilesTransferred(REL_SUCCESS, 1)
        val transferredFlowFiles = tcpServer.receivedBytes.values.map { it.toTestFlowFile() }
        assertThat(transferredFlowFiles, hasSize(equalTo(1)))
        assertThat(transferredFlowFiles[0], equalTo(flowFile))
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
        testRunner.enqueue(flowFile)

        testRunner.run()

        testRunner.assertAllFlowFilesTransferred(REL_SUCCESS, 1)
        val transferredFlowFiles = tcpServer.receivedBytes.values.map { it.toTestFlowFile() }
        assertThat(transferredFlowFiles, hasSize(equalTo(1)))
        assertThat(transferredFlowFiles[0], equalTo(flowFile))
    }

    @Test
    fun `moves FlowFile to failure relationship, when target server cannot be reached`() {
        val flowFile = TestFlowFile(
            attributes = mapOf("foo" to "bar"),
            content = "Hello failing test!".toByteArray().asList()
        )
        testRunner.setProperty(AbstractPutEventProcessor.PORT, "${port + 42}")
        testRunner.enqueue(flowFile)

        testRunner.run()

        testRunner.assertAllFlowFilesTransferred(REL_FAILURE, 1)
    }

    @Test
    fun `supports transfer of FlowFiles with large content`() {
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

        testRunner.assertAllFlowFilesTransferred(REL_SUCCESS, 1)
        testRunner.assertAllFlowFiles { flowFile ->
            assertThat(flowFile.size, equalTo(expectedByteLength))
        }
    }

    @Test
    fun `supports transfer of large amount of FlowFiles`() {
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

        testRunner.assertAllFlowFilesTransferred(REL_SUCCESS, iterations)
        val flowFiles = testRunner.getFlowFilesForRelationship(REL_SUCCESS)
        val actualIndices = flowFiles.map { it.attributes.getValue("index").toInt() }.toSet()
        assertThat(actualIndices, equalTo(expectedIndices))
    }

    @Test
    fun `supports multi-threaded execution of processor`() {
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

        testRunner.assertAllFlowFilesTransferred(REL_SUCCESS, iterations)
        val flowFiles = testRunner.getFlowFilesForRelationship(REL_SUCCESS)
        val actualIndices = flowFiles.map { it.attributes.getValue("index").toInt() }.toSet()
        assertThat(actualIndices, equalTo(expectedIndices))
    }

    @Test
    fun `supports clustered execution of processor`() {
        val iterations = 300
        val expectedIndices = (0 until iterations).toSet()
        repeat(iterations) { index ->
            val flowFile = TestFlowFile(
                attributes = mapOf("index" to "$index"),
                content = "Hello file no. $index!".toByteArray().asList()
            )
            testRunner.enqueue(flowFile)
        }
        testRunner.setClustered(true)

        testRunner.run(iterations)

        testRunner.assertAllFlowFilesTransferred(REL_SUCCESS, iterations)
        val flowFiles = testRunner.getFlowFilesForRelationship(REL_SUCCESS)
        val actualIndices = flowFiles.map { it.attributes.getValue("index").toInt() }.toSet()
        assertThat(actualIndices, equalTo(expectedIndices))
    }

    @Nested
    inner class RegardingAttributeFilter {

        @Test
        fun `supports filtering attributes to transfer with list of attribute names`() {
            val flowFile = TestFlowFile(
                attributes = mapOf(
                    "foo" to "keep",
                    "fOo" to "wrong case",
                    "bar" to "not included",
                    "other" to "keep as well",
                ),
                content = emptyList(),
            )
            testRunner.setProperty(PutFlowToTCP.ATTRIBUTES_LIST, "foo, other , missing")
            testRunner.enqueue(flowFile)

            testRunner.run()

            testRunner.assertAllFlowFilesTransferred(REL_SUCCESS, 1)
            val transferredFlowFiles = tcpServer.receivedBytes.values.map { it.toTestFlowFile() }
            assertThat(transferredFlowFiles, hasSize(equalTo(1)))
            assertThat(transferredFlowFiles[0].attributes.keys, equalTo(setOf("foo", "other", "missing")))
        }

        @Test
        fun `supports filtering attributes to transfer with attribute name regex`() {
            val flowFile = TestFlowFile(
                attributes = mapOf(
                    "foo" to "keep",
                    "fofoo" to "wrong start",
                    "food" to "wrong end",
                    "foooooooo" to "keep as well",
                ),
                content = emptyList(),
            )
            testRunner.setProperty(PutFlowToTCP.ATTRIBUTES_REGEX, "^f[o]+$")
            testRunner.enqueue(flowFile)

            testRunner.run()

            testRunner.assertAllFlowFilesTransferred(REL_SUCCESS, 1)
            val transferredFlowFiles = tcpServer.receivedBytes.values.map { it.toTestFlowFile() }
            assertThat(transferredFlowFiles, hasSize(equalTo(1)))
            assertThat(transferredFlowFiles[0].attributes.keys, equalTo(setOf("foo", "foooooooo")))
        }

        @Test
        fun `supports filtering attributes whose name are either on list or match regex`() {
            val flowFile = TestFlowFile(
                attributes = mapOf(
                    "bar" to "keep as well",
                    "noo" to "neither",
                    "foo" to "keep",
                ),
                content = emptyList(),
            )
            testRunner.setProperty(PutFlowToTCP.ATTRIBUTES_LIST, "bar")
            testRunner.setProperty(PutFlowToTCP.ATTRIBUTES_REGEX, "^foo$")
            testRunner.enqueue(flowFile)

            testRunner.run()

            testRunner.assertAllFlowFilesTransferred(REL_SUCCESS, 1)
            val transferredFlowFiles = tcpServer.receivedBytes.values.map { it.toTestFlowFile() }
            assertThat(transferredFlowFiles, hasSize(equalTo(1)))
            assertThat(transferredFlowFiles[0].attributes.keys, equalTo(setOf("bar", "foo")))
        }

        @Test
        fun `supports filtering out core attributes`() {
            val flowFile = TestFlowFile(
                attributes = emptyMap(),
                content = emptyList()
            )
            testRunner.setProperty(PutFlowToTCP.INCLUDE_CORE_ATTRIBUTES, "true")
            testRunner.enqueue(flowFile)

            testRunner.run()

            testRunner.assertAllFlowFilesTransferred(REL_SUCCESS, 1)
            val transferredFlowFiles = tcpServer.receivedBytes.values.map { it.toTestFlowFile() }
            assertThat(transferredFlowFiles, hasSize(equalTo(1)))
            assertAll(transferredFlowFiles[0].attributes.keys.map { attributeKey ->
                { assertThat(coreAttributes, hasElement(attributeKey)) }
            })
        }
    }

    @Nested
    inner class RegardingMissingValues {
        @Test
        fun `uses null for missing values, when NULL_VALUE_FOR_EMPTY_STRING is set to true`() {
            val flowFile = TestFlowFile(
                attributes = emptyMap(),
                content = emptyList()
            )
            testRunner.setProperty(PutFlowToTCP.NULL_VALUE_FOR_EMPTY_STRING, "true")
            testRunner.setProperty(PutFlowToTCP.ATTRIBUTES_LIST, "missing")
            testRunner.enqueue(flowFile)

            testRunner.run()

            testRunner.assertAllFlowFilesTransferred(REL_SUCCESS, 1)
            val transferredFlowFiles = tcpServer.receivedBytes.values.map { it.toTestFlowFile() }
            assertThat(transferredFlowFiles, hasSize(equalTo(1)))
            val attributeValues = transferredFlowFiles[0].attributes.values
            assertThat(attributeValues.size, equalTo(1))
            assertThat(attributeValues.single(), equalTo(null))
        }

        @Test
        fun `uses empty string for missing values, when NULL_VALUE_FOR_EMPTY_STRING is set to false`() {
            val flowFile = TestFlowFile(
                attributes = emptyMap(),
                content = emptyList()
            )
            testRunner.setProperty(PutFlowToTCP.NULL_VALUE_FOR_EMPTY_STRING, "false")
            testRunner.setProperty(PutFlowToTCP.ATTRIBUTES_LIST, "missing")
            testRunner.enqueue(flowFile)

            testRunner.run()

            testRunner.assertAllFlowFilesTransferred(REL_SUCCESS, 1)
            val transferredFlowFiles = tcpServer.receivedBytes.values.map { it.toTestFlowFile() }
            val attributeValues = transferredFlowFiles[0].attributes.values
            assertThat(attributeValues.size, equalTo(1))
            assertThat(attributeValues.single(), equalTo(""))
        }
    }
}
