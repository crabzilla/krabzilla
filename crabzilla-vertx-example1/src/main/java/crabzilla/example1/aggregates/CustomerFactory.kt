package crabzilla.example1.aggregates

import com.github.benmanes.caffeine.cache.Caffeine
import crabzilla.*
import crabzilla.example1.aggregates.customer.*
import crabzilla.example1.services.SampleService
import crabzilla.vertx.EntityComponentsFactory
import crabzilla.vertx.repositories.EntityUnitOfWorkRepository
import crabzilla.vertx.util.StringHelper.circuitBreakerId
import crabzilla.vertx.verticles.EntityCommandHandlerVerticle
import crabzilla.vertx.verticles.EntityCommandRestVerticle
import io.vertx.circuitbreaker.CircuitBreaker
import io.vertx.circuitbreaker.CircuitBreakerOptions
import io.vertx.core.Vertx
import io.vertx.ext.jdbc.JDBCClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class CustomerFactory @Inject
constructor(private val service: SampleService, private val vertx: Vertx, private val jdbcClient: JDBCClient)
  : EntityComponentsFactory<Customer> {

  val factory: (Customer) -> StateTracker<Customer> =  { c -> StateTracker(c, stateTransitionFn(), depInjectionFn())}

  override fun firstInstanceFn(): FirstInstanceFn<Customer> {
    return CustomerFirstInstanceFn()
  }

  override fun stateTransitionFn(): StateTransitionFn<Customer> {
    return CustomerStateTransitionFn()
  }

  override fun cmdValidatorFn(): EntityCommandValidatorFn {
    return CustomerEntityCommandValidatorFn()
  }

  override fun cmdHandlerFn(): EntityCommandHandlerFn<Customer> {
    return CustomerEntityCommandHandlerFn(factory)
  }

  override fun snapshotterFn(): VersionTracker<Customer> {
    return VersionTracker(factory)
  }

  override fun depInjectionFn(): (Customer) -> Customer {
    return { customer -> customer.copy(sampleService = service) }
  }

  override fun restVerticle(): EntityCommandRestVerticle<Customer> {
    return EntityCommandRestVerticle(vertx, Customer::class.java)
  }

  override fun cmdHandlerVerticle(): EntityCommandHandlerVerticle<Customer> {

    val cache = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build<String, Snapshot<Customer>> { key -> null } // TODO you can plug your snapshot here!

    val circuitBreaker = CircuitBreaker.create(circuitBreakerId(Customer::class.java), vertx,
            CircuitBreakerOptions()
                    .setMaxFailures(5) // number SUCCESS failure before opening the circuit
                    .setTimeout(2000) // consider a failure if the operation does not succeed in time
                    .setFallbackOnFailure(true) // do we call the fallback on failure
                    .setResetTimeout(10000) // time spent in open state before attempting to re-try
    )

    return EntityCommandHandlerVerticle(Customer::class.java, firstInstanceFn(),
            cmdValidatorFn(), cmdHandlerFn(), cache, snapshotterFn(), uowRepository(), circuitBreaker)
  }

  override fun uowRepository(): EntityUnitOfWorkRepository {
    return EntityUnitOfWorkRepository(Customer::class.java, jdbcClient)
  }


}
