package crabzilla

import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.io.Serializable
import java.util.*

// from Eventstorming

@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
interface Command : Serializable {
  val commandId: UUID
}

@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
interface DomainEvent : Serializable

interface Aggregate : Serializable {
  val id: EntityId?
  fun events(vararg event: DomainEvent): List<DomainEvent> {
    return event.asList()
  }
}

interface ExternalSystem {
  fun events(vararg event: DomainEvent): List<DomainEvent> {
    return event.asList()
  }
}

interface Listener

interface ProcessManager
