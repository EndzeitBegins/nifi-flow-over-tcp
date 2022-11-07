package io.github.endzeitbegins.nifi.flowovertcp.testing

import org.apache.nifi.flowfile.attributes.CoreAttributes

internal val coreAttributes = CoreAttributes.values().map { it.key() }