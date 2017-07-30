package crabzilla.vertx

import crabzilla.vertx.events.projection.EventProjector
import crabzilla.vertx.events.projection.ProjectionData
import java.util.function.BiFunction

interface BoundedContextComponentsFactory {

  fun eventsProjector(): EventProjector

  fun projectionRepository(): BiFunction<Long, Int, List<ProjectionData>>

  // TODO SchedulingRepository;

}
