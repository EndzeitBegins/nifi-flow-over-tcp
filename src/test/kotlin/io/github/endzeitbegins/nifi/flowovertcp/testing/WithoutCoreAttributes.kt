package io.github.endzeitbegins.nifi.flowovertcp.testing

internal fun TestFlowFile.withoutCoreAttributes() = copy(
    attributes = attributes.filterNot { (key, _) -> key in coreAttributes }
)