package crabzilla.vertx.entity.projection

import crabzilla.DomainEvent
import io.vertx.core.logging.Logger
import org.jdbi.v3.core.Jdbi


abstract class EventProjector<D>(val eventsChannelId: String,
                                 val daoClazz: Class<D>,
                                 val jdbi: Jdbi) {

  abstract val log: Logger

  fun handle(uowList: List<ProjectionData>) {

    log.info("writing ${uowList.size} units for eventsChannelId $eventsChannelId")

    val handle = jdbi.open()
    val dao = handle.attach<D>(daoClazz)

    try {

      val pairs = uowList.flatMap { pd -> pd.events.map { e -> Pair(pd.targetId, e) } }

      pairs.forEach {
        (targetId, domainEvent) -> write(dao, targetId, domainEvent)
      }

      handle.commit()

    } catch (e: Exception) {

      log.error("exception: ", e)

      handle.rollback()

    } finally {

      //h.close()

    }

    // TODO update uow_last_seq for this event channel
    log.info("wrote ${uowList.size} units for eventsChannelId $eventsChannelId")
  }

  abstract fun write(dao: D, targetId: String, event: DomainEvent)

}
