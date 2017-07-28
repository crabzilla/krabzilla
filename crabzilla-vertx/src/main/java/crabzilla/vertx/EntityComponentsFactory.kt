package crabzilla.vertx

import crabzilla.EntityFunctionsFactory
import crabzilla.vertx.repositories.EntityUnitOfWorkRepository
import crabzilla.vertx.verticles.EntityCommandHandlerVerticle
import crabzilla.vertx.verticles.EntityCommandRestVerticle

interface EntityComponentsFactory<E> : EntityFunctionsFactory<E> {

  fun restVerticle(): EntityCommandRestVerticle<E>

  fun cmdHandlerVerticle(): EntityCommandHandlerVerticle<E>

  fun uowRepository(): EntityUnitOfWorkRepository

}
