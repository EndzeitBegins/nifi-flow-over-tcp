package io.github.endzeitbegins.nifi.flowovertcp.testing.assertions

import io.github.endzeitbegins.nifi.flowovertcp.testcontainers.NiFiContainerProvider
import io.github.endzeitbegins.nifi.flowovertcp.testing.FileSystemBasedFlowFile
import io.github.endzeitbegins.nifi.flowovertcp.testing.expectWithRetry
import io.github.endzeitbegins.nifi.flowovertcp.testing.toAttributesFilePath
import io.github.endzeitbegins.nifi.flowovertcp.testing.toContentFilePath
import io.github.endzeitbegins.nifi.flowovertcp.utils.destinationDirectory
import strikt.api.expect
import strikt.assertions.hasSize
import java.time.Duration
import kotlin.io.path.listDirectoryEntries

internal fun `expect that FlowFiles were transferred`(testSet: List<FileSystemBasedFlowFile>) {
    expectWithRetry(retries = 1_200, waitTime = Duration.ofMillis(250)) {
        val attributeFiles = NiFiContainerProvider.destinationDirectory
            .listDirectoryEntries("*.attributes")

        that(attributeFiles).hasSize(testSet.size)
    }

    expect {
        for (testFileFlowFile in testSet) {
            that(testFileFlowFile.toContentFilePath()).matchesContentFrom(testFileFlowFile)
            that(testFileFlowFile.toAttributesFilePath()).containsAttributesFrom(testFileFlowFile)
        }
    }
}
