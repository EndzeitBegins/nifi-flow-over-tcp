package io.github.endzeitbegins.nifi.flowovertcp.internal.utils

import java.util.concurrent.atomic.AtomicReference

/**
 * Returns the non-null value stored in [this] [AtomicReference].
 *
 * In case the current value is null, blocks until a non-null value is set.
 */
internal fun <T: Any> AtomicReference<out T?>.getNonNull(): T {
    var nullableObject: T? = get()

    while (nullableObject == null) {
        Thread.sleep(100)
        nullableObject = get()
    }

    return nullableObject
}