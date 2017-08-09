package crabzilla.vertx.verticles

import crabzilla.DomainEvent
import crabzilla.example1.CustomerSummary
import crabzilla.example1.aggregates.CustomerActivated
import crabzilla.example1.aggregates.CustomerCreated
import crabzilla.example1.aggregates.CustomerDeactivated
import crabzilla.vertx.events.projection.EventProjector
import example1.readmodel.CustomerSummaryDao
import org.jdbi.v3.core.Jdbi

class EventsProjector4Test(eventsChannelId: String, daoClazz: Class<CustomerSummaryDao>, jdbi: Jdbi)

  : EventProjector<CustomerSummaryDao>(eventsChannelId, daoClazz, jdbi) {

  override val log = io.vertx.core.logging.LoggerFactory.getLogger(EventsProjector4Test::class.java)

  override fun write(dao: CustomerSummaryDao, targetId: String, event: DomainEvent) {
    log.info("event {} from channel {}", event, eventsChannelId)
    when (event) {
      is CustomerCreated -> dao.insert(CustomerSummary(targetId, event.name, false))
      is CustomerActivated -> dao.updateStatus(targetId, true)
      is CustomerDeactivated -> dao.updateStatus(targetId, false)
      else -> log.warn("{} does not have any event projection handler", event)
    }
  }

}
