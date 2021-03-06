package crabzilla

import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.io.Serializable
import java.util.*

// from Eventstorming

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
interface Command : Serializable {
  val commandId: UUID
}

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
interface DomainEvent : Serializable

interface Aggregate {
  val id: EntityId?
}

interface Listener

interface ProcessManager {
  val id: EntityId?
}

interface ExternalSystem
