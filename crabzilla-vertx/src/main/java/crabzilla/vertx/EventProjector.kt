package crabzilla.vertx

interface EventProjector {

  val eventsChannelId: String

  val lastUowSeq: Long

  fun handle(uowList: List<ProjectionData>)

}
