package io.github.endzeitbegins.nifi.flowovertcp.testing

internal data class TestFlowFile(
    val attributes: Map<String, String>,
    val content: List<Byte>,
)