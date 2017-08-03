package crabzilla.example1.aggregates

import crabzilla.Aggregate
import crabzilla.DomainEvent
import crabzilla.example1.services.SampleService

data class Customer(override val id: CustomerId? = null,
                    val name: String? = null,
                    val isActive: Boolean? = false,
                    val reason: String? = null,
                    val sampleService: SampleService? = null) : Aggregate {

  internal fun create(id: CustomerId, name: String): List<DomainEvent> {
    require(this.id == null, { "customer already created" })
    return events(CustomerCreated(id, name))
  }

  internal fun activate(reason: String): List<DomainEvent> {
    return events(CustomerActivated(reason, sampleService!!.now()))
  }

  internal fun deactivate(reason: String): List<DomainEvent> {
    return events(CustomerDeactivated(reason, sampleService!!.now()))
  }

}
