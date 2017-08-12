package crabzilla.vertx.entity.projection

import crabzilla.DomainEvent
import crabzilla.example1.CustomerSummary
import crabzilla.example1.customer.CustomerActivated
import crabzilla.example1.customer.CustomerCreated
import crabzilla.example1.customer.CustomerDeactivated
import crabzilla.example1.CustomerSummaryDao
import mu.KotlinLogging
import org.jdbi.v3.core.Jdbi

class EventsProjector4Test(eventsChannelId: String, daoClazz: Class<CustomerSummaryDao>, jdbi: Jdbi)

  : EventProjector<CustomerSummaryDao>(eventsChannelId, daoClazz, jdbi) {

  override val log = KotlinLogging.logger {}

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
