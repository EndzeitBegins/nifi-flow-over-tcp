package io.github.endzeitbegins.nifi.flowovertcp.internal.utils

import org.apache.nifi.logging.ComponentLog
import org.apache.nifi.processor.ProcessContext
import org.apache.nifi.processor.Processor
import org.apache.nifi.processor.Relationship
import java.time.Duration
import kotlin.concurrent.thread

/**
 * Starts a [Thread] to monitor the relationships of [this] [Processor] for availability.
 *
 * Whenever at least one relationship is unavailable,
 * because it does not have any space left to receive FlowFiles, according to its configured backpressure constraints,
 * the [onRelationshipsUnavailable] callback is invoked being passed all unavailable [Relationship]s.
 *
 * Ensure to [Thread.interrupt] the returned [Thread]
 * once the relationships should no longer be monitored to avoid resource leaks.
 */
internal fun Processor.monitorRelationshipsForAvailability(
    processContext: ProcessContext,
    logger: ComponentLog,
    monitoringPeriod: Duration = Duration.ofMillis(250),
    onRelationshipsUnavailable: (unavailableRelationships: Set<Relationship>) -> Unit,
): Thread {
    val processor = this
    val monitoringPeriodMs: Long = monitoringPeriod.toMillis()

    val monitoringThread =
        thread(start = true, name = "${processor::class.simpleName} Available Relationship Monitor") {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(monitoringPeriodMs)

                    val unavailableRelationships = processor.unavailableRelationships(processContext)

                    if (unavailableRelationships.isEmpty()) {
                        logger.debug("All relationships are available")
                    } else {
                        logger.debug("The following relationships are unavailable: $unavailableRelationships")

                        onRelationshipsUnavailable.invoke(unavailableRelationships)
                    }
                } catch (interruptedException: InterruptedException) {
                    break
                }
            }
        }

    return monitoringThread
}

/**
 * Returns all relationships of [this] [Processor] that are unavailable.
 *
 * A relationship is deemed unavailable, whenever it does not have any space left to receive FlowFiles,
 * according to its configured backpressure constraints.
 */
internal fun Processor.unavailableRelationships(processContext: ProcessContext) =
    relationships - processContext.availableRelationships

/**
 * Checks whether all relationships of [this] [Processor] are available.
 *
 * A relationship is deemed unavailable, whenever it does not have any space left to receive FlowFiles,
 * according to its configured backpressure constraints.
 */
internal fun Processor.areAllRelationshipsAvailable(processContext: ProcessContext): Boolean =
    unavailableRelationships(processContext).isEmpty()