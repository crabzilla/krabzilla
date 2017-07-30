package crabzilla

import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.io.Serializable
import java.util.*
import javax.inject.Inject

data class Reaction(val event: DomainEvent, val commands: List<Command>)

data class UnitOfWork(val id: UUID = UUID.randomUUID(),
                      val command: Command,
                      val events: List<DomainEvent>) : Serializable

@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
interface EntityId : Serializable {
  val stringValue: String
}

interface EntityCommand : Command {
  val targetId: EntityId
}

data class Version(val valueAsLong: Long) : Serializable {

  init {
    if (valueAsLong < 0) throw IllegalArgumentException("Version must be = zero or positive")
  }

  fun nextVersion(): Version {
    return Version(valueAsLong + 1)
  }

}

data class EntityUnitOfWork(val id: UUID,
                            val command: EntityCommand,
                            val events: List<DomainEvent>,
                            val version: Version) : Serializable {

  constructor(command: EntityCommand, events: List<DomainEvent>, version: Version)
          : this(UUID.randomUUID(), command, events, version)

  fun targetId(): EntityId {
    return command.targetId
  }

}

data class Snapshot<out E>(val instance: E, val version: Version) {

  fun nextVersion(): Version {
    return version.nextVersion()
  }

  val isEmpty: Boolean
    get() = version.valueAsLong == 0L

}

class StateTracker<E>(val originalInstance: E,
                      val applyEventsFn: (DomainEvent, E) -> E,
                      val dependencyInjectionFn: (E) -> E) {

  internal val stateTransitions: MutableList<StateTransition<E>> = ArrayList()

  inline fun applyEvents(function: (E) -> List<DomainEvent>): StateTracker<E> {
    val newEvents = function.invoke(currentState())
    return applyEvents(newEvents)
  }

  fun collectEvents(): List<DomainEvent> {
    return stateTransitions.map { t -> t.afterThisEvent }.toList()
  }

  fun currentState(): E {
    val current = if (isEmpty) originalInstance else stateTransitions[stateTransitions.size - 1].newInstance
    return dependencyInjectionFn.invoke(current)
  }

  val isEmpty: Boolean
    get() = stateTransitions.isEmpty()

  fun applyEvents(events: List<DomainEvent>): StateTracker<E> {
    events.forEach { e ->
      val newInstance = applyEventsFn.invoke(e, currentState())
      stateTransitions.add(StateTransition(newInstance, e))
    }
    return this
  }

  internal inner class StateTransition<out T>(val newInstance: T, val afterThisEvent: DomainEvent)

}

open class VersionTrackerFn<E> @Inject constructor(val trackerFactory: (E) -> StateTracker<E>) :
        (Snapshot<E>, Version, List<DomainEvent>) -> Snapshot<E> {

  override fun invoke(originalSnapshot: Snapshot<E>, newVersion: Version, newEvents: List<DomainEvent>): Snapshot<E> {

    if (originalSnapshot.version.valueAsLong != newVersion.valueAsLong - 1) {
      throw RuntimeException(String.format("Cannot upgrade to version %s since my version is %s",
              newVersion, originalSnapshot.version))
    }

    val tracker = trackerFactory.invoke(originalSnapshot.instance)

    return Snapshot(tracker.applyEvents({ e -> newEvents }).currentState(), newVersion)

  }

}

class UnknownCommandException(p0: String?) : RuntimeException(p0)

class ConcurrencyConflictException(p0: String?) : RuntimeException(p0)

class CommandHandlerResult(val successValue: EntityUnitOfWork? = null, val error: RuntimeException? = null) {

  fun inCaseOfSuccess(uowFn: (EntityUnitOfWork?) -> Unit) {
    if (successValue != null) {
      uowFn.invoke(successValue)
    }
  }

  fun inCaseOfError(uowFn: (RuntimeException) -> Unit) {
    if (error != null) {
      uowFn.invoke(error)
    }
  }

  companion object {

    fun unitOfWorkFn(f: () -> EntityUnitOfWork?): CommandHandlerResult {

      return try { CommandHandlerResult(successValue=f.invoke()) }
            catch (e: Throwable) { CommandHandlerResult(error=RuntimeException("unexpected error", e)) }

    }

  }

}

interface StateTransitionFn<E> : (DomainEvent, E) -> E

open interface EntityCommandValidatorFn : (EntityCommand) -> List<String>

open interface EntityCommandHandlerFn<in E> : (EntityCommand, Snapshot<E>) -> CommandHandlerResult

interface EntityFunctionsFactory<E> {

  fun seedValue(): () -> Lazy<E>

  fun stateTransitionFn(): StateTransitionFn<E>

  fun cmdValidatorFn(): EntityCommandValidatorFn

  fun cmdHandlerFn(): EntityCommandHandlerFn<E>

  fun depInjectionFn(): (E) -> E

  fun versionTrackerFn(): VersionTrackerFn<E>

}

