package io.github.endzeitbegins.nifi.flowovertcp.utils

import io.github.endzeitbegins.nifi.flowovertcp.testcontainers.NiFiContainerProvider
import java.nio.file.Path
import kotlin.io.path.div

internal val NiFiContainerProvider.destinationDirectory: Path
    get() = mountedPathOnHost / "from-nifi"

internal fun NiFiContainerProvider.clearMountedFileSystem() {
    mountedPathOnHost.deleteRegularFilesRecursively()
}