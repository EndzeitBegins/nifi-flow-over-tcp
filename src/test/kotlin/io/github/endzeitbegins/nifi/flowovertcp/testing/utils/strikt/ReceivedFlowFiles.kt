package io.github.endzeitbegins.nifi.flowovertcp.testing.utils.strikt

import io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile.TestFlowFile
import io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile.toTestFlowFile
import org.apache.nifi.processor.util.listen.AbstractListenEventProcessor
import org.apache.nifi.util.TestRunner

internal val TestRunner.receivedFlowFiles: List<TestFlowFile>
    get() = getFlowFilesForRelationship(AbstractListenEventProcessor.REL_SUCCESS).map { it.toTestFlowFile() }