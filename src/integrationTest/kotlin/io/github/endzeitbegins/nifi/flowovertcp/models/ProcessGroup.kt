package io.github.endzeitbegins.nifi.flowovertcp.models

data class ProcessGroup(
    val parentProcessGroupId: String,
    val id: String,
    val name: String,
)