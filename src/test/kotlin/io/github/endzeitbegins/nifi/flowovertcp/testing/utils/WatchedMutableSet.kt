package io.github.endzeitbegins.nifi.flowovertcp.testing.utils

internal class WatchedMutableSet<E>(private val delegate: MutableSet<E>) :
    MutableSet<E> by delegate {

    var didContainElement = false

    override fun add(element: E): Boolean {
        didContainElement = true

        return delegate.add(element)
    }

    override fun addAll(elements: Collection<E>): Boolean {
        if (elements.isNotEmpty()) {
            didContainElement = true
        }

        return delegate.addAll(elements)
    }

    override fun toString(): String =
        delegate.toString()
}

internal fun <E> MutableSet<E>.watch(): WatchedMutableSet<E> =
    WatchedMutableSet(this)