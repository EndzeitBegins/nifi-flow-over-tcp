package io.github.endzeitbegins.nifi.flowovertcp.testcontainers

import org.testcontainers.containers.BindMode
import org.testcontainers.containers.FixedHostPortGenericContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy
import java.net.ServerSocket
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

private val environment = System.getenv()

object NiFiContainerProvider {

    private val nifiVersion = environment["nifi.version"]

    private const val username: String = "admin"
    private const val password: String = "passwordWithAtLeast12Characters"

    val port: Int = ServerSocket(0).use { it.localPort }

    val mountedPathInContainer = "/tmp/mounted/"
    val mountedPathOnHost = Path("src/integrationTest/resources/mounted-directory").toAbsolutePath()

    val container: GenericContainer<*> by lazy {
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
            .withFileSystemBind("$mountedPathOnHost", mountedPathInContainer, BindMode.READ_WRITE)
            .withFileSystemBind(narPathOnHost, narPathInContainer, BindMode.READ_ONLY)
            .waitingFor(HttpWaitStrategy().forPath("/nifi"))

        fixedPortContainer.start()

        fixedPortContainer
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