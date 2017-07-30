package crabzilla.vertx

import crabzilla.EntityFunctionsFactory
import crabzilla.vertx.command.execution.EntityCommandHandlerVerticle
import crabzilla.vertx.command.execution.EntityCommandRestVerticle
import crabzilla.vertx.command.execution.EntityUnitOfWorkRepository

interface EntityComponentsFactory<E> : EntityFunctionsFactory<E> {

  fun restVerticle(): EntityCommandRestVerticle<E>

  fun cmdHandlerVerticle(): EntityCommandHandlerVerticle<E>

  fun uowRepository(): EntityUnitOfWorkRepository

}
