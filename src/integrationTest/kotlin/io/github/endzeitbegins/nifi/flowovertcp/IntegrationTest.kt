package io.github.endzeitbegins.nifi.flowovertcp

import io.github.endzeitbegins.nifi.flowovertcp.gateways.KtorNiFiApiGateway
import io.github.endzeitbegins.nifi.flowovertcp.gateways.NiFiApiGateway
import io.github.endzeitbegins.nifi.flowovertcp.gateways.setUpNiFiFlow
import io.github.endzeitbegins.nifi.flowovertcp.testcontainers.NiFiContainerProvider
import io.github.endzeitbegins.nifi.flowovertcp.testing.assertions.contentAsJson
import io.github.endzeitbegins.nifi.flowovertcp.testing.assertions.sha256HashSum
import io.github.endzeitbegins.nifi.flowovertcp.testing.waitFor
import io.github.endzeitbegins.nifi.flowovertcp.utils.deleteRegularFilesRecursively
import io.github.endzeitbegins.nifi.flowovertcp.utils.sha256
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import strikt.api.expect
import strikt.assertions.hasEntry
import strikt.assertions.isEqualTo
import strikt.java.allBytes
import strikt.java.exists
import strikt.java.size
import java.nio.file.*
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeoutException
import kotlin.io.path.*
import kotlin.random.Random


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntegrationTest {

    private val random = Random(42)

    init {
        NiFiContainerProvider.mountedPathOnHost.deleteRegularFilesRecursively()

        val niFiApiGateway: NiFiApiGateway = KtorNiFiApiGateway
        niFiApiGateway.setUpNiFiFlow()
    }

    @Test
    internal fun `supports transfer of FlowFiles with both content and attributes`() {
        val testSet = List(1) {
            val fileSize = random.nextInt(1_024, 64 * 1_024)
            val fileContent = random.nextBytes(fileSize)
            val fileName = "${UUID.nameUUIDFromBytes(fileContent)}"

            val targetPath = NiFiContainerProvider.mountedPathOnHost / "to-nifi" / fileName
            targetPath.writeBytes(fileContent)

            TestFile(
                fileName = fileName,
                fileSize = fileSize.toLong(),
                fileSha256Hash = fileContent.sha256,
            )
        }

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
            for (testFile in testSet) {
                val contentFilePath = destinationDirectory / testFile.fileName
                val attributesFilePath = destinationDirectory / "${testFile.fileName}.attributes"

                that(contentFilePath)
                    .exists()
                    .and { size.isEqualTo(testFile.fileSize) }
                    .and { allBytes().sha256HashSum.isEqualTo(testFile.fileSha256Hash) }

                that(attributesFilePath)
                    .exists()
                    .contentAsJson()
                    .hasEntry("filename", testFile.fileName)
            }
        }
    }
}

private data class TestFile(
    val fileName: String,
    val fileSize: Long,
    val fileSha256Hash: String,
)
