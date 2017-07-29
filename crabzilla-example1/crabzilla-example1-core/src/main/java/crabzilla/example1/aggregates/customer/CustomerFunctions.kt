package crabzilla.example1.aggregates.customer

import com.github.kittinunf.result.Result
import crabzilla.*
import javax.inject.Inject

class CustomerFirstInstanceFn : FirstInstanceFn<Customer> {

  override fun invoke(): Lazy<Customer> {
    return lazyOf(Customer())
  }

}

class CustomerStateTransitionFn : StateTransitionFn<Customer> {

  override fun invoke(event: DomainEvent, customer: Customer): Customer {

    return when(event) {
      is CustomerCreated -> customer.copy(id = event.id, name =  event.name)
      is CustomerActivated -> customer.copy(isActive = true, reason = event.reason)
      is CustomerDeactivated ->customer.copy(isActive = false, reason = event.reason)
      else -> customer
    }

  }

}

class CustomerEntityCommandValidatorFn : EntityCommandValidatorFn {

  override fun invoke(command: EntityCommand): List<String> {
    return listOf() // TODO implement. Right now all commands are valid
  }

}

class CustomerEntityCommandHandlerFn @Inject constructor(val trackerFactory: (Customer) -> StateTracker<Customer>) :
        EntityCommandHandlerFn<Customer> {

  override fun invoke(command: EntityCommand, snapshot: Snapshot<Customer>): Result<EntityUnitOfWork, Exception> {

    val target = snapshot.instance
    val newVersion = snapshot.version

    return Result.of( when (command) {

      is CreateCustomerCmd -> EntityUnitOfWork(command, target.create(command.targetId, command.name), newVersion)

      is ActivateCustomerCmd -> EntityUnitOfWork(command, target.activate(command.reason), newVersion)

      is DeactivateCustomerCmd -> EntityUnitOfWork(command, target.deactivate(command.reason), newVersion)

      is CreateActivateCustomerCmd -> {
        val tracker = trackerFactory.invoke(target)
        val events = tracker
                .applyEvents({ customer -> customer.create(command.targetId, command.name) })
                .applyEvents({ customer -> customer.activate(command.reason) })
                .collectEvents()
        EntityUnitOfWork(command, events, newVersion)
      }

      else -> null
    })

  }

}
