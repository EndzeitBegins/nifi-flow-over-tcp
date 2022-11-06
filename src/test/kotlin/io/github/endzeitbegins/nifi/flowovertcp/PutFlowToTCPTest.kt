package io.github.endzeitbegins.nifi.flowovertcp

import net.nerdfunk.nifi.processors.PutFlow2TCP
import org.apache.nifi.processor.Processor
import org.apache.nifi.util.TestRunner
import org.apache.nifi.util.TestRunners
import org.junit.jupiter.api.Test

class PutFlowToTCPTest {

    private val testRunner = newTestRunner<PutFlow2TCP>()

    init {
        testRunner.setProperty(PutFlow2TCP.PORT, "12345") // TODO?
    }

    @Test
    fun `supports transfer of FlowFiles with no additional attributes and empty content`() {
        val content = ByteArray(size = 0)
        val attributes = emptyMap<String, String>()

        testRunner.enqueue(content, attributes)
        testRunner.run()

        testRunner.assertAllFlowFilesTransferred(PutFlow2TCP.REL_SUCCESS, 1)
    }

    /*
        TODO
         - no attributes, no content
         - only attributes
         - only content
         - no receiver
         - large file
         - large amount?
         - with / without core attributes
     */


}

private inline fun <reified TestedProcessor: Processor> newTestRunner(): TestRunner =
    TestRunners.newTestRunner(TestedProcessor::class.java)