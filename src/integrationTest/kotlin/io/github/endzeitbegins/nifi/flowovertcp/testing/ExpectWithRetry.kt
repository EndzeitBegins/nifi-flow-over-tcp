package io.github.endzeitbegins.nifi.flowovertcp.testing

import strikt.api.ExpectationBuilder
import strikt.api.expect
import java.time.Duration

internal fun expectWithRetry(
    retries: Int,
    waitTime: Duration,
    block: suspend ExpectationBuilder.() -> Unit,
) {
    var remainingAttempts = retries + 1

    while (remainingAttempts > 0) {
        try {
            expect(block)

            break
        } catch (assertionError: AssertionError) {
            remainingAttempts -= 1

            if (remainingAttempts > 0) {
                Thread.sleep(waitTime.toMillis())
            } else throw assertionError
        }
    }
}