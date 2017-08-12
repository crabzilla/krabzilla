package crabzilla.example1.customer

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
      is CreateCustomer ->
        if (command.name.equals("a bad name"))
          listOf("Invalid name: ${command.name}") else listOf()
      else -> listOf() // all other commands are valid
    }
  }
}

class CustomerCommandHandlerFn (val trackerFactory: (Customer) -> StateTracker<Customer>) : // <4>
        (EntityCommand, Snapshot<Customer>) -> CommandHandlerResult {

  override fun invoke(cmd: EntityCommand, snapshot: Snapshot<Customer>): CommandHandlerResult {

    val customer = snapshot.instance
    val newVersion = snapshot.version.nextVersion()

    return resultOf {
      when (cmd) {
        is CreateCustomer -> uowOf(cmd, customer.create(cmd.targetId, cmd.name), newVersion)
        is ActivateCustomer -> uowOf(cmd, customer.activate(cmd.reason), newVersion)
        is DeactivateCustomer -> uowOf(cmd, customer.deactivate(cmd.reason), newVersion)
        is CreateActivateCustomer -> {
          val tracker = trackerFactory.invoke(customer)
          val events = tracker
                  .applyEvents({ c -> c.create(cmd.targetId, cmd.name) })
                  .applyEvents({ c -> c.activate(cmd.reason) })
                  .collectEvents()
          uowOf(cmd, events, newVersion)
        }
        else -> throw UnknownCommandException("for command ${cmd.javaClass.simpleName}")
      }
    }
  }
}
