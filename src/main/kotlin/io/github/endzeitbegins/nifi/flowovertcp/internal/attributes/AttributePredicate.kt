package io.github.endzeitbegins.nifi.flowovertcp.internal.attributes

internal typealias AttributePredicate = Attribute.() -> Boolean

internal infix fun AttributePredicate.or(rhs: AttributePredicate): AttributePredicate {
    val lhs = this

    return { lhs(this) || rhs(this) }
}