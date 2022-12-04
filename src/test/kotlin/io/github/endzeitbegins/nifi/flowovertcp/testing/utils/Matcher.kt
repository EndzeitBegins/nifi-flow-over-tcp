package io.github.endzeitbegins.nifi.flowovertcp.testing.utils

import com.natpryce.hamkrest.Matcher

internal fun <T> not(matcher: Matcher<T>): Matcher<T> = matcher.not()