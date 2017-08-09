package crabzilla.vertx.events.projection

import crabzilla.DomainEvent
import java.io.Serializable
import java.util.*

class ProjectionData(val uowId: UUID, val uowSequence: Long, val targetId: String, val events: List<DomainEvent>)
  : Serializable
