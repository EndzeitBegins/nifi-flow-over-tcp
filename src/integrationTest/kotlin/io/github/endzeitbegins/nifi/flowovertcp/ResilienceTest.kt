package io.github.endzeitbegins.nifi.flowovertcp

import io.github.endzeitbegins.nifi.flowovertcp.nifi.gateways.KtorNiFiApiGateway
import io.github.endzeitbegins.nifi.flowovertcp.nifi.gateways.NiFiApiGateway
import io.github.endzeitbegins.nifi.flowovertcp.nifi.withTestFlow
import io.github.endzeitbegins.nifi.flowovertcp.testcontainers.NiFiContainerProvider
import io.github.endzeitbegins.nifi.flowovertcp.testing.FlowFileSeed
import io.github.endzeitbegins.nifi.flowovertcp.testing.assertions.`expect that FlowFiles were transferred`
import io.github.endzeitbegins.nifi.flowovertcp.testing.provideToNiFiFlow
import io.github.endzeitbegins.nifi.flowovertcp.utils.clearMountedFileSystem
import io.github.endzeitbegins.nifi.flowovertcp.utils.destinationDirectory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEmpty
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
            niFiApiGateway.stopProcessor(listenFlowFromTcpProcessor.id)

            val random = Random(4711)
            val testSetSeed = generateTestSeed(random = random, flowFileCount = 1_000)

            val testSet = testSetSeed.provideToNiFiFlow(random)

            Thread.sleep(5_000)
            expectThat(NiFiContainerProvider.destinationDirectory.listDirectoryEntries("*.attributes")).isEmpty()

            niFiApiGateway.startProcessor(listenFlowFromTcpProcessor.id)
            `expect that FlowFiles were transferred`(testSet)
        }

    private fun generateTestSeed(random: Random, flowFileCount: Int) = List(flowFileCount) { index ->
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
}