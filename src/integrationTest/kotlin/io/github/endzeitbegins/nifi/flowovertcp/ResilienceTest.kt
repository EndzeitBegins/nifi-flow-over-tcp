package io.github.endzeitbegins.nifi.flowovertcp

import io.github.endzeitbegins.nifi.flowovertcp.nifi.gateways.KtorNiFiApiGateway
import io.github.endzeitbegins.nifi.flowovertcp.nifi.gateways.NiFiApiGateway
import io.github.endzeitbegins.nifi.flowovertcp.nifi.withTestFlow
import io.github.endzeitbegins.nifi.flowovertcp.testcontainers.NiFiContainerProvider
import io.github.endzeitbegins.nifi.flowovertcp.testing.FlowFileSeed
import io.github.endzeitbegins.nifi.flowovertcp.testing.assertions.`expect that FlowFiles were transferred`
import io.github.endzeitbegins.nifi.flowovertcp.testing.expectWithRetry
import io.github.endzeitbegins.nifi.flowovertcp.testing.provideToNiFiFlow
import io.github.endzeitbegins.nifi.flowovertcp.utils.clearMountedFileSystem
import io.github.endzeitbegins.nifi.flowovertcp.utils.destinationDirectory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import java.time.Duration
import kotlin.io.path.listDirectoryEntries
import kotlin.random.Random

class ResilienceTest {

    private val niFiApiGateway: NiFiApiGateway = KtorNiFiApiGateway(
        port = NiFiContainerProvider.port
    )

    @BeforeEach
    fun setUp() {
        niFiApiGateway.stopProcessGroup("root")

        NiFiContainerProvider.clearMountedFileSystem()
    }

    @Test
    fun `does NOT lose any information, when target host is temporarily unavailable`() =
        niFiApiGateway.withTestFlow { niFiTestFlow ->
            val listenFlowFromTcpProcessor = niFiTestFlow.processors.receiveFlowFile
            stopProcessor(listenFlowFromTcpProcessor.id)

            val random = Random(4711)
            val testSetSeed = generateTestSeed(random = random, flowFileCount = 1_000)

            val testSet = testSetSeed.provideToNiFiFlow(random)

            Thread.sleep(5_000)
            expectThat(NiFiContainerProvider.destinationDirectory.listDirectoryEntries("*.attributes")).isEmpty()

            startProcessor(listenFlowFromTcpProcessor.id)
            `expect that FlowFiles were transferred`(testSet)
        }

    @Test
    fun `supports backpressure from downstream`(): Unit =
        niFiApiGateway.withTestFlow(startProcessGroup = false) { niFiTestFlow ->
            val objectThreshold = 42
            val connectionBeforePutFlowFileToTCP =
                niFiTestFlow.connections.receiveFlowFileToAdjustFilenameForContentFile
            val connectionAfterListenFlowFromTCP =
                niFiTestFlow.connections.receiveFlowFileToAdjustFilenameForContentFile
            updateConnectionBackPressure(
                id = connectionAfterListenFlowFromTCP.id,
                backPressureDataSizeThreshold = null,
                backPressureObjectThreshold = objectThreshold
            )
            startProcessGroup(niFiTestFlow.rootProcessGroup.id)
            val adjustFilenameForContentFileProcessor = niFiTestFlow.processors.adjustFilenameForContentFile
            stopProcessor(adjustFilenameForContentFileProcessor.id)

            val random = Random(2573)
            val initialTestSet =
                generateTestSeed(random = random, flowFileCount = objectThreshold).provideToNiFiFlow(random)
            expectWithRetry(retries = 360, waitTime = Duration.ofMillis(250)) {
                that(countFlowFilesInQueueOfConnection(id = connectionAfterListenFlowFromTCP.id))
                    .describedAs("Count of FlowFiles in queue of connection after ListenFlowFromTCP")
                    .isEqualTo(initialTestSet.size)
            }

            val additionalTestSet = generateTestSeed(random = random, flowFileCount = 58).provideToNiFiFlow(random)
            expectWithRetry(retries = 360, waitTime = Duration.ofMillis(250)) {
                that(countFlowFilesInQueueOfConnection(id = connectionBeforePutFlowFileToTCP.id))
                    .describedAs("Count of FlowFiles in queue of connection before PutFlowFileToTCP")
                    .isEqualTo(additionalTestSet.size)
                that(countFlowFilesInQueueOfConnection(id = connectionAfterListenFlowFromTCP.id))
                    .describedAs("Count of FlowFiles in queue of connection after ListenFlowFromTCP")
                    .isEqualTo(initialTestSet.size)
            }

            startProcessor(adjustFilenameForContentFileProcessor.id)
            `expect that FlowFiles were transferred`(initialTestSet + additionalTestSet)
        }

    private var baseIndex = 0
    private fun generateTestSeed(random: Random, flowFileCount: Int): List<FlowFileSeed> {
        val testSeed = List(flowFileCount) { iterationIndex ->
            val index = baseIndex + iterationIndex
            val fileName = "generated-$index"
            val fileSize = random.nextInt(1_024, 64 * 1_024)

            FlowFileSeed(
                fileNamePrefix = fileName,
                fileSize = fileSize,
                attributes = mapOf(
                    "index" to "$index",
                    "custom.filename" to fileName
                )
            )
        }

        baseIndex += flowFileCount

        return testSeed
    }
}