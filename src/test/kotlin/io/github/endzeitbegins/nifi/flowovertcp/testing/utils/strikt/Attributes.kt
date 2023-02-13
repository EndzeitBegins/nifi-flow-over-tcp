package io.github.endzeitbegins.nifi.flowovertcp.testing.utils.strikt

import io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile.TestFlowFile
import strikt.api.Assertion
import strikt.api.DescribeableBuilder

internal fun Assertion.Builder<TestFlowFile>.attributes(): DescribeableBuilder<Map<String, String?>> {
    return get(TestFlowFile::attributes)
}