package io.github.endzeitbegins.nifi.flowovertcp

import io.github.endzeitbegins.nifi.flowovertcp.nifi.gateways.KtorNiFiApiGateway
import io.github.endzeitbegins.nifi.flowovertcp.nifi.gateways.NiFiApiGateway
import io.github.endzeitbegins.nifi.flowovertcp.nifi.withTestFlow
import io.github.endzeitbegins.nifi.flowovertcp.testcontainers.NiFiContainerProvider
import io.github.endzeitbegins.nifi.flowovertcp.testing.*
import io.github.endzeitbegins.nifi.flowovertcp.testing.assertions.`expect that FlowFiles were transferred`
import io.github.endzeitbegins.nifi.flowovertcp.utils.clearMountedFileSystem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

class CoreFunctionsTest {

    private val niFiApiGateway: NiFiApiGateway = KtorNiFiApiGateway(
        port = NiFiContainerProvider.port
    )

    @BeforeEach
    fun setUp() {
        niFiApiGateway.stopProcessGroup("root")

        NiFiContainerProvider.clearMountedFileSystem()
    }

    @Test
    internal fun `supports transfer of FlowFiles with both content and attributes`() = niFiApiGateway.withTestFlow {
        val random = Random(42)
        val testSetSeed = List(1_000) { index ->
            val fileName = "both-$index"
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

        val testSet = testSetSeed.provideToNiFiFlow(random)

        `expect that FlowFiles were transferred`(testSet)
    }

    @Test
    internal fun `supports transfer of FlowFiles with content but no additional attributes`() =
        niFiApiGateway.withTestFlow {
            val random = Random(21)
            val testSetSeed = List(1_000) { index ->
                val fileName = "content-$index"
                val fileSize = random.nextInt(1_024, 64 * 1_024)

                FlowFileSeed(
                    fileNamePrefix = fileName,
                    fileSize = fileSize,
                    attributes = emptyMap()
                )
            }

            val testSet = testSetSeed.provideToNiFiFlow(random)

            `expect that FlowFiles were transferred`(testSet)
        }

    @Test
    internal fun `supports transfer of FlowFiles with additional attributes but empty content`() =
        niFiApiGateway.withTestFlow {
            val testSetSeed = List(1_000) { index ->
                val fileName = "content-$index"
                val fileSize = 0

                FlowFileSeed(
                    fileNamePrefix = fileName,
                    fileSize = fileSize,
                    attributes = mapOf(
                        "index" to "$index",
                        "custom.filename" to fileName
                    )
                )
            }

            val testSet = testSetSeed.provideToNiFiFlow(Random(69))

            `expect that FlowFiles were transferred`(testSet)
        }
}
