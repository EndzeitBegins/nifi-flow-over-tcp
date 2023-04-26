package io.github.endzeitbegins.nifi.flowovertcp

import io.github.endzeitbegins.nifi.flowovertcp.gateways.KtorNiFiApiGateway
import io.github.endzeitbegins.nifi.flowovertcp.gateways.NiFiApiGateway
import io.github.endzeitbegins.nifi.flowovertcp.gateways.setUpNiFiFlow
import io.github.endzeitbegins.nifi.flowovertcp.testcontainers.NiFiContainerProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import strikt.api.expect
import strikt.assertions.isEqualTo
import strikt.java.allBytes
import strikt.java.exists
import strikt.java.size
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
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
        val testSet = List(1_000) {
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

        waitFor(
            duration = Duration.ofSeconds(150),
            errorMessage = "Not all attributes were received after timeout has been reached!"
        ) {
            val attributeFiles = destinationDirectory
                .listDirectoryEntries("*.attributes")

            attributeFiles.size >= testSet.size
        }

        expect {
            for (testFile in testSet) {
                val contentFilePath = destinationDirectory / testFile.fileName
                val attributesFilePath = destinationDirectory / "${testFile.fileName}.attributes"

                that(contentFilePath)
                    .exists()
                    .and { size.isEqualTo(testFile.fileSize) }
                    .and { allBytes().get(ByteArray::sha256).isEqualTo(testFile.fileSha256Hash) }

                that(attributesFilePath)
                    .exists()
                // todo test attributes exist ...
            }
        }
    }


}

private fun Path.deleteRegularFilesRecursively() {
    Files.walkFileTree(this, object : FileVisitor<Path> {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            if (!Files.isHidden(file)) {
                file.deleteExisting()
            }

            return FileVisitResult.CONTINUE
        }

        override fun visitFileFailed(file: Path, exc: IOException?): FileVisitResult {
            if (exc != null) {
                throw exc
            }

            return FileVisitResult.CONTINUE
        }

        override fun postVisitDirectory(dir: Path?, exc: IOException?): FileVisitResult {
            if (exc != null) {
                throw exc
            }

            return FileVisitResult.CONTINUE
        }

    })
//    @OptIn(ExperimentalPathApi::class)
//    this.visitFileTree {
//        onVisitFile { file, _ ->
//            if (!file.isHidden()) {
//                file.deleteExisting()
//            }
//
//            FileVisitResult.CONTINUE
//        }
//    }
}

private data class TestFile(
    val fileName: String,
    val fileSize: Long,
    val fileSha256Hash: String,
)

private val ByteArray.sha256: String
    get() {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(this)

        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }


private fun waitFor(
    duration: Duration,
    errorMessage: String = "Failed to meet predicate after $duration.",
    predicate: () -> Boolean,
) {
    val startTime = Instant.now()
    val timeout = startTime.plus(duration)

    do {
        if (predicate()) {
            return
        }

        Thread.sleep(250)
    } while (Instant.now() < timeout)

    throw TimeoutException(errorMessage)
}


