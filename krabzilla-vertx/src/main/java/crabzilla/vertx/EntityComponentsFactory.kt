package crabzilla.vertx

import crabzilla.EntityFunctionsFactory
import crabzilla.SnapshotUpgraderFn
import crabzilla.vertx.commands.execution.EntityCommandHandlerVerticle
import crabzilla.vertx.commands.execution.EntityCommandRestVerticle
import crabzilla.vertx.commands.execution.EntityUnitOfWorkRepository

interface EntityComponentsFactory<E> : EntityFunctionsFactory<E> {
  fun depInjectionFn(): (E) -> E
  fun snapshotUpgrader(): SnapshotUpgraderFn<E>
  fun restVerticle(): EntityCommandRestVerticle<E>
  fun cmdHandlerVerticle(): EntityCommandHandlerVerticle<E>
  fun uowRepository(): EntityUnitOfWorkRepository
}
