package crabzilla.vertx.entity

import crabzilla.EntityFunctionsFactory
import crabzilla.Snapshot
import crabzilla.SnapshotUpgraderFn
import crabzilla.StateTracker
import crabzilla.vertx.util.StringHelper
import io.vertx.circuitbreaker.CircuitBreaker
import io.vertx.circuitbreaker.CircuitBreakerOptions
import io.vertx.core.Vertx
import io.vertx.ext.jdbc.JDBCClient
import net.jodah.expiringmap.ExpirationPolicy
import net.jodah.expiringmap.ExpiringMap
import java.util.concurrent.TimeUnit

interface EntityComponentsFactory<E> : EntityFunctionsFactory<E> {
  
  fun clazz(): Class<E>

  fun depInjectionFn(): (E) -> E

  fun vertx(): Vertx
  
  fun jdbcClient(): JDBCClient

  // default implementations
  
  fun trackerFactory() : (E) -> StateTracker<E> =  { e -> StateTracker(e, stateTransitionFn(), depInjectionFn()) }

  fun snapshotUpgrader(): SnapshotUpgraderFn<E> {
    return SnapshotUpgraderFn(trackerFactory())
  }

  fun restVerticle(): EntityCommandRestVerticle<E> {
    return EntityCommandRestVerticle(clazz())
  }

  fun cmdHandlerVerticle(): EntityCommandHandlerVerticle<E> {

    val cache : ExpiringMap<String, Snapshot<E>> = ExpiringMap.builder()
            .maxSize(10_000)
            .expiration(10, TimeUnit.MINUTES)
            .expirationPolicy(ExpirationPolicy.ACCESSED)
            .build()

    val circuitBreaker = CircuitBreaker.create(StringHelper.circuitBreakerId(clazz()), vertx(),
            CircuitBreakerOptions()
                    .setMaxFailures(5) // number SUCCESS failure before opening the circuit
                    .setTimeout(2000) // consider a failure if the operation does not succeed in time
                    .setFallbackOnFailure(true) // do we call the fallback on failure
                    .setResetTimeout(10000) // time spent in open state before attempting to re-try
    )

    return EntityCommandHandlerVerticle(clazz(), seedValueFn().invoke(),
            cmdValidatorFn(), cmdHandlerFn(), cache, snapshotUpgrader(), uowRepository(), circuitBreaker)
  }

  fun uowRepository(): EntityUnitOfWorkRepository {
    return EntityUnitOfWorkRepository(clazz(), jdbcClient())
  }

}
