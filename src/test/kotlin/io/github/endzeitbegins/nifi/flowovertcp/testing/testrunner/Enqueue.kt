package io.github.endzeitbegins.nifi.flowovertcp.testing.testrunner

import io.github.endzeitbegins.nifi.flowovertcp.testing.TestFlowFile
import org.apache.nifi.util.TestRunner

internal fun TestRunner.enqueue(flowFile: TestFlowFile) {
    enqueue(flowFile.content.toByteArray(), flowFile.attributes)
}