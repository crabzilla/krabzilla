package crabzilla.example1.aggregates

import crabzilla.*

class CustomerSeedValueFn : () -> Lazy<Customer> {

  override fun invoke(): Lazy<Customer> {
    return lazyOf(Customer())
  }

}

class CustomerStateTransitionFn : StateTransitionFn<Customer> {

  override fun invoke(event: DomainEvent, customer: Customer): Customer {

    return when(event) {
      is CustomerCreated -> customer.copy(id = event.id, name =  event.name)
      is CustomerActivated -> customer.copy(isActive = true, reason = event.reason)
      is CustomerDeactivated -> customer.copy(isActive = false, reason = event.reason)
      else -> customer
    }

  }

}

class CustomerCommandValidatorFn : EntityCommandValidatorFn {

  override fun invoke(command: EntityCommand): List<String> {
    return listOf() // TODO implement. Right now all commands are valid
  }

}

class CustomerCommandHandlerFn (val trackerFactory: (Customer) -> StateTracker<Customer>) :
        EntityCommandHandlerFn<Customer> {

  override fun invoke(command: EntityCommand, snapshot: Snapshot<Customer>): CommandHandlerResult {

    val target = snapshot.instance
    val newVersion = snapshot.version.nextVersion()

    return result {

      when (command) {

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

        else -> throw UnknownCommandException("for command ${command.javaClass.simpleName}")

      }

    }

  }

}
