package io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile

import org.apache.nifi.util.TestRunner

internal fun TestRunner.enqueue(flowFile: TestFlowFile) {
    enqueue(flowFile.content.toByteArray(), flowFile.attributes)
}