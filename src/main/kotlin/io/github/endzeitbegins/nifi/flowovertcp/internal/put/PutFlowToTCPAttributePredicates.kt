package io.github.endzeitbegins.nifi.flowovertcp.internal.put

import io.github.endzeitbegins.nifi.flowovertcp.internal.attributes.AttributePredicate
import io.github.endzeitbegins.nifi.flowovertcp.internal.attributes.coreAttributes
import org.apache.nifi.flowfile.attributes.CoreAttributes

internal fun matchesRegex(regex: Regex): AttributePredicate = { this.key.matches(regex) }
internal fun isIn(set: Set<String>): AttributePredicate = { this.key in set }
internal val isCoreAttribute: AttributePredicate = isIn(coreAttributes)
internal val isFlowFileUuid: AttributePredicate = { this.key == CoreAttributes.UUID.key() }