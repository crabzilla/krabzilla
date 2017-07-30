package crabzilla.vertx.events.projection

interface EventProjector {

  val eventsChannelId: String

  val lastUowSeq: Long

  fun handle(uowList: List<ProjectionData>)

}
