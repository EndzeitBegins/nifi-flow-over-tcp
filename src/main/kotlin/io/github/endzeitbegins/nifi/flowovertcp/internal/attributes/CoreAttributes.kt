package io.github.endzeitbegins.nifi.flowovertcp.internal.attributes

import org.apache.nifi.flowfile.attributes.CoreAttributes

internal val coreAttributes = CoreAttributes.values().map { it.key() }.toSet()