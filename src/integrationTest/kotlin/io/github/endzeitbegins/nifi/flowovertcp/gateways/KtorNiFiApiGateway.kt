package io.github.endzeitbegins.nifi.flowovertcp.gateways

import io.github.endzeitbegins.nifi.flowovertcp.models.*
import io.github.endzeitbegins.nifi.flowovertcp.testcontainers.NiFiContainerProvider
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object KtorNiFiApiGateway : NiFiApiGateway {

    private val client = HttpClient(CIO) {
        install(Logging)

        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    private val niFiUrl = "http://localhost:" + NiFiContainerProvider.port + "/nifi-api"

    override fun createProcessor(
        parentProcessGroupId: String,
        type: String,
        name: String,
        properties: Map<String, String>,
        position: Position,
        autoTerminatedRelationships: Set<String>,
    ): Processor {
        val body = ProcessorEntity(
            id = null,
            component = ProcessorDTO(
                name = name,
                type = type,
                config = ProcessorConfigDTO(
                    properties = properties,
                    autoTerminatedRelationships = autoTerminatedRelationships
                ),
                position = position.toPositionDTO(),
            ),
            revision = RevisionDTO(
                clientId = "fixed-client-id",
                version = 0,
            ),
        )

        val createdProcessorEntity = runBlocking {
            val response = client.post("$niFiUrl/process-groups/$parentProcessGroupId/processors") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }

            response.body<ProcessorEntity>()
        }

        return Processor(
            parentProcessGroupId = parentProcessGroupId,
            id = checkNotNull(createdProcessorEntity.id),
        )
    }

    override fun createConnection(
        parentProcessGroupId: String,
        source: ConnectionSource,
        destination: ConnectionDestination,
    ): Connection {
        runBlocking {
            client.post("$niFiUrl/process-groups/$parentProcessGroupId/connections") {
                contentType(ContentType.Application.Json)
                setBody(
                    ConnectionEntity(
                        component = ConnectionDTO(
                            source = ConnectableDTO(
                                groupId = source.parentProcessGroupId,
                                id = source.sourceId,
                                type = "PROCESSOR",
                            ),

                            destination = ConnectableDTO(
                                groupId = destination.parentProcessGroupId,
                                id = destination.destinationId,
                                type = "PROCESSOR",
                            ),

                            selectedRelationships = (source as? ConnectionSource.Processor)?.relationships ?: emptySet()
                        ),
                        revision = RevisionDTO(
                            clientId = "meh",
                            version = 0,
                        ),
                    )
                )
            }
        }

        return Connection
    }

    override fun startProcessGroup(id: String) {
        runBlocking {
            client.put("$niFiUrl/flow/process-groups/$id") {
                contentType(ContentType.Application.Json)
                setBody(
                    ScheduleComponentsEntity(
                        id = id,
                        state = "RUNNING",
                    )
                )
            }
        }
    }
}

private fun Position.toPositionDTO(): PositionDTO =
    PositionDTO(x = x.toDouble(), y = y.toDouble())

@Serializable
private data class ConnectionEntity(
    val revision: RevisionDTO,
    val component: ConnectionDTO,
)

@Serializable
private data class ConnectionDTO(
    val source: ConnectableDTO,
    val destination: ConnectableDTO,
    val selectedRelationships: Set<String>,
)

@Serializable
private data class ConnectableDTO(
    val groupId: String,
    val id: String,
    val type: String,
)

@Serializable
private data class ProcessorEntity(
    val id: String?,
    val revision: RevisionDTO,
    val component: ProcessorDTO,
)

@Serializable
private data class PositionDTO(
    val x: Double,
    val y: Double,
)

@Serializable
private data class ProcessorDTO(
    val name: String,
    val type: String,
    val config: ProcessorConfigDTO,
    val position: PositionDTO,
)

@Serializable
private data class RevisionDTO(
    val clientId: String,
    val version: Int,
)

@Serializable
private data class ProcessorConfigDTO(
    val properties: Map<String, String?>,
    val autoTerminatedRelationships: Set<String> = emptySet(),
)

@Serializable
private data class ScheduleComponentsEntity(
    val id: String,
    val state: String,
)