package crabzilla.vertx

import java.util.function.BiFunction

interface BoundedContextComponentsFactory {

  fun eventsProjector(): EventProjector

  fun projectionRepository(): BiFunction<Long, Int, List<ProjectionData>>

  // TODO SchedulingRepository;

}
