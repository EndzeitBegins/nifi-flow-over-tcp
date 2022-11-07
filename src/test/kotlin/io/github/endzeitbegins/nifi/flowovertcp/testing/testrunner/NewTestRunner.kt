package io.github.endzeitbegins.nifi.flowovertcp.testing.testrunner

import org.apache.nifi.processor.Processor
import org.apache.nifi.util.TestRunner
import org.apache.nifi.util.TestRunners

internal inline fun <reified TestedProcessor : Processor> newTestRunner(init: TestRunner.() -> Unit = {}): TestRunner {
    val testRunner = TestRunners.newTestRunner(TestedProcessor::class.java)
    testRunner.init()

    return testRunner
}