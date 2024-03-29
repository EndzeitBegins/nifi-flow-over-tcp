package io.github.endzeitbegins.nifi.flowovertcp

import io.github.endzeitbegins.nifi.flowovertcp.PutFlowToTCP.Companion.INCLUDE_CORE_ATTRIBUTES
import io.github.endzeitbegins.nifi.flowovertcp.internal.attributes.coreAttributes
import io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile.TestFlowFile
import io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile.enqueue
import io.github.endzeitbegins.nifi.flowovertcp.testing.tcp.testTcpServer
import io.github.endzeitbegins.nifi.flowovertcp.testing.testrunner.newTestRunner
import io.github.endzeitbegins.nifi.flowovertcp.testing.utils.strikt.attributes
import io.github.endzeitbegins.nifi.flowovertcp.testing.utils.strikt.keys
import io.github.endzeitbegins.nifi.flowovertcp.testing.utils.strikt.transferredFlowFiles
import org.apache.nifi.flowfile.attributes.CoreAttributes
import org.apache.nifi.processor.util.put.AbstractPutEventProcessor.*
import org.apache.nifi.remote.io.socket.NetworkUtils
import org.junit.jupiter.api.*
import strikt.api.expectThat
import strikt.assertions.*
import java.io.InputStream
import kotlin.random.Random

class PutFlowToTCPTest {

    private val tcpServer = testTcpServer()

    init {
        tcpServer.start()
    }

    private val port = tcpServer.listeningPort

    private val testRunner = newTestRunner<PutFlowToTCP> {
        threadCount = 1
        setClustered(false)

        setProperty(INCLUDE_CORE_ATTRIBUTES, "false")
        setProperty(PORT, "$port")
        setProperty(CONNECTION_PER_FLOWFILE, "true")
    }

    @AfterEach
    fun setUp() {
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
        expectThat(tcpServer.transferredFlowFiles) {
            hasSize(1)
            contains(flowFile)
        }
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
        expectThat(tcpServer.transferredFlowFiles) {
            hasSize(1)
            contains(flowFile)
        }
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
        expectThat(tcpServer.transferredFlowFiles) {
            hasSize(1)
            contains(flowFile)
        }
    }

    @Test
    fun `moves FlowFile to failure relationship, when target server cannot be reached`() {
        val flowFile = TestFlowFile(
            attributes = mapOf("foo" to "bar"),
            content = "Hello failing test!".toByteArray().asList()
        )
        testRunner.setProperty(PORT, "${port + 42}")
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
        val data: InputStream = object : InputStream() {
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
            expectThat(flowFile.size).isEqualTo(expectedByteLength)
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
        expectThat(actualIndices).isEqualTo(expectedIndices)
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
        expectThat(actualIndices).isEqualTo(expectedIndices)
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
        expectThat(actualIndices).isEqualTo(expectedIndices)
    }

    @Test
    fun `supports transfer of multiple FlowFiles over a single connection`() {
        val iterations = 300
        val expectedIndices = (0 until iterations).toSet()
        repeat(iterations) { index ->
            val flowFile = TestFlowFile(
                attributes = mapOf("index" to "$index"),
                content = "Hello file no. $index!".toByteArray().asList()
            )
            testRunner.enqueue(flowFile)
        }
        testRunner.setProperty(CONNECTION_PER_FLOWFILE, "false")

        testRunner.run(iterations)

        testRunner.assertAllFlowFilesTransferred(REL_SUCCESS, iterations)
        val flowFiles = testRunner.getFlowFilesForRelationship(REL_SUCCESS)
        val actualIndices = flowFiles.map { it.attributes.getValue("index").toInt() }.toSet()
        expectThat(actualIndices).isEqualTo(expectedIndices)
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
            expectThat(tcpServer.transferredFlowFiles) {
                hasSize(1)
                single().attributes().and {
                    containsKeys("foo", "other", "missing")
                    doesNotContainKeys("fOo")
                }
            }
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
            expectThat(tcpServer.transferredFlowFiles) {
                hasSize(1)
                single().attributes().and {
                    containsKeys("foo", "foooooooo")
                    doesNotContainKeys("fofoo", "food")
                }
            }
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
            expectThat(tcpServer.transferredFlowFiles) {
                hasSize(1)
                single().attributes().and {
                    containsKeys("bar", "foo")
                    doesNotContainKeys("noo")
                }
            }
        }

        @Test
        fun `supports filtering for core attributes`() {
            val flowFile = TestFlowFile(
                attributes = emptyMap(),
                content = emptyList()
            )
            testRunner.setProperty(INCLUDE_CORE_ATTRIBUTES, "true")
            testRunner.enqueue(flowFile)

            testRunner.run()

            testRunner.assertAllFlowFilesTransferred(REL_SUCCESS, 1)
            expectThat(tcpServer.transferredFlowFiles) {
                hasSize(1)
                single().attributes().keys().all {
                    isContainedIn(coreAttributes)
                }
            }
        }

        @Test
        internal fun `does NOT transfer FlowFile uuid, because it cannot be set on destination`() {
            val flowFile = TestFlowFile(
                attributes = emptyMap(),
                content = emptyList()
            )
            testRunner.setProperty(INCLUDE_CORE_ATTRIBUTES, "true")
            testRunner.enqueue(flowFile)

            testRunner.run()

            testRunner.assertAllFlowFilesTransferred(REL_SUCCESS, 1)
            expectThat(tcpServer.transferredFlowFiles) {
                hasSize(1)
                single().attributes().and {
                    doesNotContainKey(CoreAttributes.UUID.key())
                }
            }
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
            expectThat(tcpServer.transferredFlowFiles) {
                hasSize(1)
                single().attributes().and {
                    hasEntry("missing", null)
                }
            }
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
            expectThat(tcpServer.transferredFlowFiles) {
                hasSize(1)
                single().attributes().and {
                    hasEntry("missing", "")
                }
            }
        }
    }
}
