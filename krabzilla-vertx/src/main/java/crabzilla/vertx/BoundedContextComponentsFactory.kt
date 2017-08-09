package crabzilla.vertx

import crabzilla.vertx.entity.projection.EventProjector
import crabzilla.vertx.entity.projection.ProjectionData
import java.util.function.BiFunction

interface BoundedContextComponentsFactory {

  fun eventsProjector(): EventProjector<Any>

  fun projectionRepository(): BiFunction<Long, Int, List<ProjectionData>>

  // TODO SchedulingRepository;

}
