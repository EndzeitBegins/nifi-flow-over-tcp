package io.github.endzeitbegins.nifi.flowovertcp.testcontainers

import io.github.endzeitbegins.nifi.flowovertcp.testcontainers.NiFiContainerProvider.container
import io.github.endzeitbegins.nifi.flowovertcp.testcontainers.NiFiContainerProvider.mountedPathInContainer
import io.github.endzeitbegins.nifi.flowovertcp.testcontainers.NiFiContainerProvider.mountedPathOnHost
import io.github.endzeitbegins.nifi.flowovertcp.testcontainers.NiFiContainerProvider.port
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.FixedHostPortGenericContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.div
import kotlin.io.path.setPosixFilePermissions

private val environment = System.getenv()

/**
 * Provides a running [container] of the apache/nifi OCI image at the dynamic [port].
 *
 * The .nar-file of nifi-flow-over-tcp is integrated into the NiFi instance.
 * The [mountedPathOnHost] is mounted in the container at [mountedPathInContainer].
 */
object NiFiContainerProvider {

    private val nifiVersion = environment["nifi.version"]

    private const val username: String = "admin"
    private const val password: String = "passwordWithAtLeast12Characters"

    const val mountedPathInContainer = "/tmp/mounted/"
    val mountedPathOnHost: Path = Path("src/integrationTest/resources/mounted-directory").toAbsolutePath()

    private const val logPathInContainer = "/opt/nifi/nifi-current/logs/"
    private val logPathOnHost = (mountedPathOnHost / "nifi-logs").toAbsolutePath()

    init {
        // TODO  !
        val allowAll = setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_WRITE,
            PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ,
            PosixFilePermission.OTHERS_WRITE,
            PosixFilePermission.OTHERS_EXECUTE,
            )
        mountedPathOnHost.setPosixFilePermissions(allowAll)
        for (path in Files.list(mountedPathOnHost)) {
            path.setPosixFilePermissions(allowAll)
        }
    }

    val container: GenericContainer<*> by lazy {
        val port: Int = ServerSocket(0).use { it.localPort }
        val narFilePath: Path = locateNarFile()

        val narPathOnHost = narFilePath.absolutePathString()
        val narPathInContainer = "/opt/nifi/nifi-current/extensions/library.nar"

        @Suppress("DEPRECATION")
        val fixedPortContainer = FixedHostPortGenericContainer("apache/nifi:$nifiVersion")
            .withEnv(
                mapOf(
                    "NIFI_WEB_HTTPS_PORT" to "",
                    "NIFI_WEB_HTTP_PORT" to "$port",
                    "SINGLE_USER_CREDENTIALS_USERNAME" to username,
                    "SINGLE_USER_CREDENTIALS_PASSWORD" to password,
                )
            )
            .withFixedExposedPort(port, port)
            .withExposedPorts(port)
            .withFileSystemBind("$logPathOnHost", logPathInContainer, BindMode.READ_WRITE)
            .withFileSystemBind("$mountedPathOnHost", mountedPathInContainer, BindMode.READ_WRITE)
            .withFileSystemBind(narPathOnHost, narPathInContainer, BindMode.READ_ONLY)
            .waitingFor(HttpWaitStrategy().forPath("/nifi"))


        println("""
            ############################################################
            Starting up container with OCI image apache/nifi.
            
            This may take a while ...
            ############################################################
        """.trimIndent())

        fixedPortContainer.start()

        // TODO vvvv
        println("--- here i am ---")
        println(mountedPathOnHost)
        println(logPathOnHost)
        val res1 = fixedPortContainer.execInContainer("ls -la /opt/nifi/nifi-current")
        println(res1.stdout)
        println(res1.stderr)
        val res2 = fixedPortContainer.execInContainer("ls -la /opt/nifi/nifi-current/logs")
        println(res2.stdout)
        println(res2.stderr)
        val res3 = fixedPortContainer.execInContainer("ls -la /tmp/mounted/")
        println(res3.stdout)
        println(res3.stderr)
        val res4 = fixedPortContainer.execInContainer("ls -la /opt/nifi/nifi-current/extensions/")
        println(res4.stdout)
        println(res4.stderr)
        fixedPortContainer.followOutput(Slf4jLogConsumer(LoggerFactory.getLogger("nifi-container-logs"))) // TODO ?!
        println("--- here i end ---")
        // TODO ^^^^


        println("""
            ############################################################
            Container with OCI image apache/nifi started.
            ############################################################
        """.trimIndent())

        fixedPortContainer
    }

    val port: Int by lazy {
        container.firstMappedPort
    }

    private fun locateNarFile(): Path {
        val pathSeparator = System.getProperty("file.separator")
        val classpath = System.getProperty("java.class.path")

        val narFileRegex = """^nifi-flow-over-tcp.*[.]nar$""".toRegex()

        return classpath
            .split(":")
            .filter { it.substringAfterLast(pathSeparator).matches(narFileRegex) }
            .map { Path(it) }
            .single()
    }
}