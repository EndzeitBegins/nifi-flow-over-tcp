package io.github.endzeitbegins.nifi.flowovertcp

import org.testcontainers.containers.BindMode
import org.testcontainers.containers.FixedHostPortGenericContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy
import java.net.ServerSocket
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

private val environment = System.getenv()

object NiFiContainerProvider {

    private val nifiVersion = environment["nifi.version"]

    const val username: String = "admin"
    const val password: String = "passwordWithAtLeast12Characters"

    val port: Int = ServerSocket(0).use { it.localPort }

    val container: GenericContainer<*> by lazy {
        val narFilePath: Path = locateNarFile()

        val hostPath = narFilePath.absolutePathString()
        val containerPath = "/opt/nifi/nifi-current/extensions/library.nar"

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
            .withFileSystemBind(hostPath, containerPath, BindMode.READ_ONLY)
            .waitingFor(HttpWaitStrategy().forPath("/nifi"))

        fixedPortContainer.start()

        fixedPortContainer
    }

    private fun locateNarFile(): Path {
        val pathSeparator = System.getProperty("file.separator")
        val classpath = System.getProperty("java.class.path")

        return classpath
            .split(pathSeparator)
            .filter { it.matches("""^nifi-flow-over-tcp.*[.]nar$""".toRegex()) }
            .map { Path(it) }
            .single()
    }
}