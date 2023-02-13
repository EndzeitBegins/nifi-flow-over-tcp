package io.github.endzeitbegins.nifi.flowovertcp.testing.utils.strikt

import strikt.api.Assertion
import strikt.api.DescribeableBuilder

internal fun <A, B> Assertion.Builder<Map<A, B>>.keys(): DescribeableBuilder<Set<A>> {
    return get(Map<A, B>::keys)
}