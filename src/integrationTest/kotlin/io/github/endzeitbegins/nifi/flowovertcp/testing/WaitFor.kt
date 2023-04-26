package io.github.endzeitbegins.nifi.flowovertcp.testing

import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeoutException

internal fun waitFor(
    duration: Duration,
    onTimeout: () -> TimeoutException,
    predicate: () -> Boolean,
) {
    val startTime = Instant.now()
    val timeout = startTime.plus(duration)

    do {
        if (predicate()) {
            return
        }

        Thread.sleep(250)
    } while (Instant.now() < timeout)

    throw onTimeout()
}