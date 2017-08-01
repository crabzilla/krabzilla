package crabzilla.example1.aggregates

import crabzilla.*
import crabzilla.example1.services.SampleService
import crabzilla.vertx.EntityComponentsFactory
import crabzilla.vertx.commands.execution.EntityCommandHandlerVerticle
import crabzilla.vertx.commands.execution.EntityCommandRestVerticle
import crabzilla.vertx.commands.execution.EntityUnitOfWorkRepository
import crabzilla.vertx.util.StringHelper.circuitBreakerId
import io.vertx.circuitbreaker.CircuitBreaker
import io.vertx.circuitbreaker.CircuitBreakerOptions
import io.vertx.core.Vertx
import io.vertx.ext.jdbc.JDBCClient
import net.jodah.expiringmap.ExpirationPolicy
import net.jodah.expiringmap.ExpiringMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class CustomerFactory @Inject
constructor(private val service: SampleService, private val vertx: Vertx, private val jdbcClient: JDBCClient)
  : EntityComponentsFactory<Customer> {

  val factory: (Customer) -> StateTracker<Customer> =  { c -> StateTracker(c, stateTransitionFn(), depInjectionFn())}

  override fun seedValueFn(): () -> Lazy<Customer> {
    return CustomerSeedValueFn()
  }

  override fun stateTransitionFn(): StateTransitionFn<Customer> {
    return CustomerStateTransitionFn()
  }

  override fun cmdValidatorFn(): EntityCommandValidatorFn {
    return CustomerCommandValidatorFn()
  }

  override fun cmdHandlerFn(): EntityCommandHandlerFn<Customer> {
    return CustomerCommandHandlerFn(factory)
  }

  override fun snapshotUpgrader(): SnapshotUpgraderFn<Customer> {
    return SnapshotUpgraderFn(factory)
  }

  override fun depInjectionFn(): (Customer) -> Customer {
    return { customer -> customer.copy(sampleService = service) }
  }

  override fun restVerticle(): EntityCommandRestVerticle<Customer> {
    return EntityCommandRestVerticle(vertx, Customer::class.java)
  }

  override fun cmdHandlerVerticle(): EntityCommandHandlerVerticle<Customer> {

    val cache : ExpiringMap<String, Snapshot<Customer>> = ExpiringMap.builder()
            .maxSize(10_000)
            .expiration(10, TimeUnit.MINUTES)
            .expirationPolicy(ExpirationPolicy.ACCESSED)
            .entryLoader<String, Snapshot<Customer>> { key -> null } // TODO how to plug a non blocking loader ?
            .build()

    val circuitBreaker = CircuitBreaker.create(circuitBreakerId(Customer::class.java), vertx,
            CircuitBreakerOptions()
                    .setMaxFailures(5) // number SUCCESS failure before opening the circuit
                    .setTimeout(2000) // consider a failure if the operation does not succeed in time
                    .setFallbackOnFailure(true) // do we call the fallback on failure
                    .setResetTimeout(10000) // time spent in open state before attempting to re-try
    )

    return EntityCommandHandlerVerticle(Customer::class.java, seedValueFn().invoke(),
            cmdValidatorFn(), cmdHandlerFn(), cache, snapshotUpgrader(), uowRepository(), circuitBreaker)
  }

  override fun uowRepository(): EntityUnitOfWorkRepository {
    return EntityUnitOfWorkRepository(Customer::class.java, jdbcClient)
  }


}
