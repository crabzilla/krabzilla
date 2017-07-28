package crabzilla.vertx

import crabzilla.EntityUnitOfWork
import java.io.Serializable
import java.util.*

class CommandExecution(val result: RESULT, val commandId: UUID?, val constraints: List<String>? = null,
                       val uowSequence: Long? = null, val unitOfWork: EntityUnitOfWork? = null) : Serializable {

  enum class RESULT {
    FALLBACK,
    VALIDATION_ERROR,
    HANDLING_ERROR,
    CONCURRENCY_ERROR,
    UNKNOWN_COMMAND,
    SUCCESS,
    COMMAND_ALREADY_PROCESSED // TODO
  }

}
