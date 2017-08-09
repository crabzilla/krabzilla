package crabzilla.example1.aggregates

import crabzilla.*

class CustomerSeedValueFn : () -> Lazy<Customer> { // <1>
  override fun invoke(): Lazy<Customer> {
    return lazyOf(Customer())
  }
}

class CustomerStateTransitionFn : (DomainEvent, Customer) -> Customer { // <2>
  override fun invoke(event: DomainEvent, customer: Customer): Customer {
    return when(event) {
      is CustomerCreated -> customer.copy(id = event.id, name =  event.name)
      is CustomerActivated -> customer.copy(isActive = true, reason = event.reason)
      is CustomerDeactivated -> customer.copy(isActive = false, reason = event.reason)
      else -> customer
    }
  }
}

class CustomerCommandValidatorFn : (EntityCommand) -> List<String> { // <3>
  override fun invoke(command: EntityCommand): List<String> {
    return when(command) {
      is CreateCustomerCmd ->
        if (command.name.equals("a bad name"))
          listOf("Invalid name: ${command.name}") else listOf()
      else -> listOf() // all other commands are valid
    }
  }
}

class CustomerCommandHandlerFn (val trackerFactory: (Customer) -> StateTracker<Customer>) : // <4>
        EntityCommandHandlerFn<Customer> {
  override fun invoke(command: EntityCommand, snapshot: Snapshot<Customer>): CommandHandlerResult {

    val customer = snapshot.instance
    val newVersion = snapshot.version.nextVersion()

    return result {
      when (command) {
        is CreateCustomerCmd ->
          EntityUnitOfWork(command, customer.create(command.targetId, command.name), newVersion)
        is ActivateCustomerCmd ->
          EntityUnitOfWork(command, customer.activate(command.reason), newVersion)
        is DeactivateCustomerCmd ->
          EntityUnitOfWork(command, customer.deactivate(command.reason), newVersion)
        is CreateActivateCustomerCmd -> {
          val tracker = trackerFactory.invoke(customer)
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
