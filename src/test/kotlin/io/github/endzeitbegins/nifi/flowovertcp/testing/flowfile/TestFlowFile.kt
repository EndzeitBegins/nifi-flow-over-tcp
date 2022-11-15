package io.github.endzeitbegins.nifi.flowovertcp.testing.flowfile

internal data class TestFlowFile(
    val attributes: Map<String, String?>,
    val content: List<Byte>,
)