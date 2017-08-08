package crabzilla.example1


import crabzilla.DomainEvent
import crabzilla.example1.aggregates.CustomerActivated
import crabzilla.example1.aggregates.CustomerCreated
import crabzilla.example1.aggregates.CustomerDeactivated
import crabzilla.vertx.events.projection.EventProjector
import crabzilla.vertx.events.projection.ProjectionData
import example1.readmodel.CustomerSummaryDao
import org.jdbi.v3.core.Jdbi

class Example1EventProjector(override val eventsChannelId: String, val jdbi: Jdbi) : EventProjector {

  val log = io.vertx.core.logging.LoggerFactory.getLogger(Example1EventProjector::class.java)

  override val lastUowSeq: Long
    get() = TODO("not implemented")

  override fun handle(uowList: List<ProjectionData>) {
    log.info("writing ${uowList.size} units for eventsChannelId ${eventsChannelId}")
    val handle = jdbi.open()
    val dao = handle.attach<CustomerSummaryDao>(CustomerSummaryDao::class.java)
    try {
      uowList.flatMap { pd -> pd.events.map { e -> Pair(pd.targetId, e) } }
              .forEach { pair -> _handle(dao, pair.first, pair.second) }
      handle.commit()
    } catch (e: Exception) {
      log.error("exception: ", e)
      handle.rollback()
    } finally {
      //h.close()
    }
    log.info("wrote ${uowList.size} units for eventsChannelId ${eventsChannelId}")
  }

  internal fun _handle(dao: CustomerSummaryDao, id: String, event: DomainEvent) {
    log.info("event {} from channel {}", event, eventsChannelId)
    when (event) {
      is CustomerCreated -> dao.insert(CustomerSummary(id, event.name, false))
      is CustomerActivated -> dao.updateStatus(id, true)
      is CustomerDeactivated -> dao.updateStatus(id, false)
      else -> log.warn("{} does not have any event projection handler", event)
    }
    // TODO update uow_last_seq for this event channel
  }

}
