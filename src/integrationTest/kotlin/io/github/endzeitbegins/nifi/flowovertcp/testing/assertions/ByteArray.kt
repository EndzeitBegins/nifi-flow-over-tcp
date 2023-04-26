package io.github.endzeitbegins.nifi.flowovertcp.testing.assertions

import io.github.endzeitbegins.nifi.flowovertcp.utils.sha256
import strikt.api.Assertion
import strikt.api.DescribeableBuilder

internal val Assertion.Builder<ByteArray>.sha256HashSum: DescribeableBuilder<String>
    get() = get(ByteArray::sha256)