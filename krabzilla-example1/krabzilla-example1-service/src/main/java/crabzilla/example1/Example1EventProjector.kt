package crabzilla.example1


import crabzilla.DomainEvent
import crabzilla.example1.aggregates.CustomerActivated
import crabzilla.example1.aggregates.CustomerCreated
import crabzilla.example1.aggregates.CustomerDeactivated
import crabzilla.vertx.events.projection.EventProjector
import crabzilla.vertx.events.projection.ProjectionData
import example1.datamodel.Tables.CUSTOMER_SUMMARY
import example1.datamodel.tables.records.CustomerSummaryRecord
import org.jooq.Configuration
import org.jooq.impl.DSL


class Example1EventProjector(override val eventsChannelId: String, val jooqCfg: Configuration) : EventProjector {

  val log = io.vertx.core.logging.LoggerFactory.getLogger(Example1EventProjector::class.java)

  override val lastUowSeq: Long
    get() = TODO("not implemented")

  override fun handle(uowList: List<ProjectionData>) {

    log.info("writing {} units for eventsChannelId {}", uowList.size, eventsChannelId)

    DSL.using(jooqCfg)
       .transaction { ctx ->
         uowList.flatMap { pd -> pd.events.map { e -> Pair(pd.targetId, e) } }
                .forEach { pair -> handle(ctx, pair.first, pair.second) }
       }

    log.info("wrote {} units for eventsChannelId {}", uowList.size, eventsChannelId)
  }


  internal fun handle(ctx: Configuration, id: String, event: DomainEvent) {

    log.info("event {} from channel {}", event, eventsChannelId)

    when(event) {
      is CustomerCreated -> {
        DSL.using(ctx).insertInto<CustomerSummaryRecord>(CUSTOMER_SUMMARY)
                .values(id, event.name, false).execute()
      }
      is CustomerActivated -> {
        DSL.using(ctx).update<CustomerSummaryRecord>(CUSTOMER_SUMMARY)
                .set(CUSTOMER_SUMMARY.IS_ACTIVE, true)
                .where(CUSTOMER_SUMMARY.ID.eq(id)).execute()
      }
      is CustomerDeactivated -> {
        DSL.using(ctx).update<CustomerSummaryRecord>(CUSTOMER_SUMMARY)
                .set(CUSTOMER_SUMMARY.IS_ACTIVE, false)
                .where(CUSTOMER_SUMMARY.ID.eq(id)).execute()
      }
      else -> log.warn("{} does not have any event projection handler", event)
    }

    // TODO update uow_last_seq for this event channel

  }

}
