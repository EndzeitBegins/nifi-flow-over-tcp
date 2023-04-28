package io.github.endzeitbegins.nifi.flowovertcp

import io.github.endzeitbegins.nifi.flowovertcp.gateways.KtorNiFiApiGateway
import io.github.endzeitbegins.nifi.flowovertcp.gateways.NiFiApiGateway
import io.github.endzeitbegins.nifi.flowovertcp.nifi.withTestFlow
import io.github.endzeitbegins.nifi.flowovertcp.testcontainers.NiFiContainerProvider
import io.github.endzeitbegins.nifi.flowovertcp.testing.FileSystemBasedFlowFile
import io.github.endzeitbegins.nifi.flowovertcp.testing.FlowFileSeed
import io.github.endzeitbegins.nifi.flowovertcp.testing.assertions.contentAsJson
import io.github.endzeitbegins.nifi.flowovertcp.testing.assertions.sha256HashSum
import io.github.endzeitbegins.nifi.flowovertcp.testing.toFileSystemBasedFlowFile
import io.github.endzeitbegins.nifi.flowovertcp.testing.waitFor
import io.github.endzeitbegins.nifi.flowovertcp.utils.deleteRegularFilesRecursively
import io.github.endzeitbegins.nifi.flowovertcp.utils.sha256
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.assertions.hasEntry
import strikt.assertions.isEqualTo
import strikt.java.allBytes
import strikt.java.exists
import strikt.java.size
import java.time.Duration
import java.util.concurrent.TimeoutException
import kotlin.io.path.*
import kotlin.random.Random


class CoreFunctionsTest {

    private val random = Random(42)
    private val niFiApiGateway: NiFiApiGateway = KtorNiFiApiGateway

    @BeforeEach
    fun setUp() {
        niFiApiGateway.stopProcessGroup("root")
        NiFiContainerProvider.mountedPathOnHost.deleteRegularFilesRecursively()
    }

    @Test
    internal fun `supports transfer of FlowFiles with both content and attributes`() = niFiApiGateway.withTestFlow {
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

        val destinationDirectory = NiFiContainerProvider.mountedPathOnHost / "from-nifi"

        var attributeFileCount = 0
        waitFor(
            duration = Duration.ofSeconds(300),
            onTimeout = {
                TimeoutException(
                    "Only $attributeFileCount of expected ${testSet.size} attribute files were received after timeout has been reached!"
                )
            }
        ) {
            val attributeFiles = destinationDirectory
                .listDirectoryEntries("*.attributes")
            attributeFileCount = attributeFiles.size

            attributeFileCount >= testSet.size
        }

        expect {
            for (testFileFlowFile in testSet) {
                val contentFilePath = destinationDirectory / "${testFileFlowFile.baseFileName}.content"
                val attributesFilePath = destinationDirectory / "${testFileFlowFile.baseFileName}.attributes"

                that(contentFilePath)
                    .exists()
                    .and { size.isEqualTo(testFileFlowFile.fileSize) }
                    .and { allBytes().sha256HashSum.isEqualTo(testFileFlowFile.fileSha256Hash) }

                that(attributesFilePath)
                    .exists()
                    .contentAsJson().and {
                        for (attribute in testFileFlowFile.attributes) {
                            hasEntry(attribute.key, attribute.value)
                        }
                        hasEntry("content_SHA-256", testFileFlowFile.fileSha256Hash)
                    }
            }
        }
    }
}

internal fun Map<String, String>.toJson() =
    Json.encodeToString(this)

internal fun List<FlowFileSeed>.provideToNiFiFlow(random: Random): List<FileSystemBasedFlowFile> =
    map { seed ->
        val targetDirectory = NiFiContainerProvider.mountedPathOnHost / "to-nifi"

        val contentTargetPath = targetDirectory / "${seed.fileNamePrefix}.content"
        val fileContent = random.nextBytes(seed.fileSize)
        contentTargetPath.writeBytes(fileContent)

        val attributesTargetPath = targetDirectory / "${seed.fileNamePrefix}.attributes"
        val attributesJson = seed.attributes.toJson()
        attributesTargetPath.writeText(attributesJson)

        seed.toFileSystemBasedFlowFile(fileContent.sha256)
    }