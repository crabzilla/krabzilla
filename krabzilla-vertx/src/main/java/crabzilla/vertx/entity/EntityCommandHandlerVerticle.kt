package crabzilla.vertx.entity

import crabzilla.*
import crabzilla.vertx.entity.EntityCommandExecution.RESULT
import crabzilla.vertx.util.StringHelper.commandHandlerId
import io.vertx.circuitbreaker.CircuitBreaker
import io.vertx.core.AbstractVerticle
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.eventbus.Message
import mu.KotlinLogging
import net.jodah.expiringmap.ExpiringMap

class EntityCommandHandlerVerticle<E>(internal val aggregateRootClass: Class<E>,
                                      internal val seedValue: Lazy<E>,
                                      internal val validatorFn: (EntityCommand) -> List<String>,
                                      internal val cmdHandlerFn: (EntityCommand, Snapshot<E>) -> CommandHandlerResult,
                                      internal val cache: ExpiringMap<String, Snapshot<E>>,
                                      internal val snapshotPromoterFn: SnapshotPromoterFn<E>,
                                      internal val eventRepository: EntityUnitOfWorkRepository,
                                      internal val circuitBreaker: CircuitBreaker) : AbstractVerticle() {

  private val log = KotlinLogging.logger {}

  @Throws(Exception::class)
  override fun start() {

    val consumer = vertx.eventBus().consumer<EntityCommand>(commandHandlerId(aggregateRootClass))

    consumer.handler({ msg ->

      val command = msg.body()

      if (command == null) {

        val cmdExecResult = EntityCommandExecution(commandId = command, result = RESULT.VALIDATION_ERROR,
                constraints = listOf("Command cannot be null. Check if JSON payload is valid."))
        msg.reply(cmdExecResult)
        return@handler
      }

      log.info("received a command $command")

      val constraints = validatorFn.invoke(command)

      if (!constraints.isEmpty()) {

        val result = EntityCommandExecution(commandId = command.commandId, result = RESULT.VALIDATION_ERROR,
                constraints = constraints)
        msg.reply(result)
        return@handler
      }

      circuitBreaker.fallback { throwable ->

        log.error("Fallback for command $command.commandId", throwable)
        val cmdExecResult = EntityCommandExecution(commandId = command.commandId, result = RESULT.FALLBACK)
        msg.reply(cmdExecResult)

      }

              .execute<EntityCommandExecution>(cmdHandler(command))

              .setHandler(resultHandler(msg))

    })

  }

  internal fun cmdHandler(command: EntityCommand): (Future<EntityCommandExecution>) -> Unit {

    return { future1 ->

      val targetId = command.targetId.stringValue

      log.info("cache.get($targetId)")

      val cachedSnapshot = cache[targetId] ?: Snapshot(seedValue.value, Version(0))
      // TODO look for the current snapshot in a repo when cache does not contain targetId

      log.info("id $targetId cached lastSnapshotData has version ${cachedSnapshot.version} " +
              "Will check if there any version beyond it")

      val selectAfterVersionFuture = Future.future<SnapshotData>()

      eventRepository.selectAfterVersion(targetId, cachedSnapshot.version, selectAfterVersionFuture)

        selectAfterVersionFuture.setHandler { snapshotDataAsyncResult ->

        if (snapshotDataAsyncResult.failed()) {
          future1.fail(snapshotDataAsyncResult.cause())
          return@setHandler
        }

        val nonCached = snapshotDataAsyncResult.result()

        val totalOfNonCachedEvents = nonCached.events.size

        val resultingSnapshot = if (totalOfNonCachedEvents > 0)
          snapshotPromoterFn.invoke(cachedSnapshot, nonCached.version, nonCached.events)
        else
          cachedSnapshot

          log.info("id $targetId found $totalOfNonCachedEvents pending events. " +
                  "Last version is now ${resultingSnapshot.version}")

          if (totalOfNonCachedEvents > 0) {
          cache.put(targetId, resultingSnapshot)
        }

        // cmd handler _may_ be blocking. Otherwise, aggregate root would need to use reactive API to call
        // external services
        vertx.executeBlocking<EntityCommandExecution>({ future2 ->

          val cmdHandlerResult = cmdHandlerFn.invoke(command, resultingSnapshot)

          cmdHandlerResult.inCaseOfSuccess({ uow ->

            log.info("Command handling success for ${command.commandId} -> $uow")

            val appendFuture = Future.future<Long>()

            eventRepository.append(uow!!, appendFuture)

            appendFuture.setHandler { appendAsyncResult ->

              if (appendAsyncResult.failed()) {

                val error = appendAsyncResult.cause()

                log.error("When appending uow of command ${command.commandId} -> ${error.message}")

                when (error) {

                  is ConcurrencyConflictException -> {
                    val cmdExecResult = EntityCommandExecution(commandId = command.commandId,
                            result = RESULT.CONCURRENCY_ERROR,
                            constraints = listOf("" + error.message))
                    future2.complete(cmdExecResult)
                  }

                  is Throwable -> {
                    future2.fail(error)
                  }

                }

                return@setHandler

              }

              val cmdExecResult = EntityCommandExecution(commandId = command.commandId,
                      result = RESULT.SUCCESS,
                      unitOfWork = uow, uowSequence = appendAsyncResult.result())

              val finalSnapshot = snapshotPromoterFn.invoke(resultingSnapshot, uow.version, uow.events)

              cache.put(targetId, finalSnapshot)

              future2.complete(cmdExecResult)


            }

          })

          cmdHandlerResult.inCaseOfError({ error ->

            log.error("Command handling error for $command -> $error.message")

            val cmdExecResult = when (error) {

              is UnknownCommandException -> EntityCommandExecution(commandId = command.commandId,
                      result = RESULT.UNKNOWN_COMMAND,
                      constraints = listOf("" + error.message))

              else -> EntityCommandExecution(commandId = command.commandId, result = RESULT.HANDLING_ERROR,
                      constraints = listOf("" + error.message))

            }

            future2.complete(cmdExecResult)

          })

        }, false, { res ->

          if (res.succeeded()) {
            println("The result is: ${res.result()}")
            future1.complete(res.result())
          } else {
            println("The error is: ${res.cause()}")
            future1.fail(res.cause())
          }

        })

      }

    }

  }

  internal fun resultHandler(msg: Message<EntityCommand>): (AsyncResult<EntityCommandExecution>) -> Unit {

    return { resultHandler: AsyncResult<EntityCommandExecution> ->

      if (resultHandler.succeeded()) {

        val resp = resultHandler.result()
        log.info("success: $resp")
        msg.reply(resp)

      } else {

        log.error("error cause: $resultHandler.cause()")
        log.error("error message: $resultHandler.cause().message")
        resultHandler.cause().printStackTrace()
        // TODO customize conform commandResult
        msg.fail(400, resultHandler.cause().message)
      }

    }
  }

}

