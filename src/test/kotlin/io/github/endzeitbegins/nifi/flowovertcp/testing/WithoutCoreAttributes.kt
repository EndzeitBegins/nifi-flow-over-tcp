package io.github.endzeitbegins.nifi.flowovertcp.testing

import io.github.endzeitbegins.nifi.flowovertcp.internal.attributes.coreAttributes

internal fun TestFlowFile.withoutCoreAttributes() = copy(
    attributes = attributes.filterNot { (key, _) -> key in coreAttributes }
)