package crabzilla.example1.aggregates

import crabzilla.DomainEvent
import crabzilla.EntityCommand
import crabzilla.EntityCommandHandlerFn
import crabzilla.example1.services.SampleInternalService
import crabzilla.vertx.entity.EntityComponentsFactory
import io.vertx.core.Vertx
import io.vertx.ext.jdbc.JDBCClient
import javax.inject.Inject

class CustomerFactory @Inject
constructor(private val internalService: SampleInternalService, private val vertx: Vertx, private val jdbcClient: JDBCClient)
  : EntityComponentsFactory<Customer> {

  override fun clazz(): Class<Customer> {
    return Customer::class.java
  }

  override fun seedValueFn(): () -> Lazy<Customer> {
    return CustomerSeedValueFn()
  }

  override fun stateTransitionFn(): (DomainEvent, Customer) -> Customer {
    return CustomerStateTransitionFn()
  }

  override fun cmdValidatorFn(): (EntityCommand) -> List<String> {
    return CustomerCommandValidatorFn()
  }

  override fun cmdHandlerFn(): EntityCommandHandlerFn<Customer> {
    return CustomerCommandHandlerFn(trackerFactory())
  }

  override fun depInjectionFn(): (Customer) -> Customer {
    return { customer -> customer.copy(sampleInternalService = internalService) }
  }

  override fun vertx(): Vertx {
    return vertx
  }

  override fun jdbcClient(): JDBCClient {
    return jdbcClient
  }

}
