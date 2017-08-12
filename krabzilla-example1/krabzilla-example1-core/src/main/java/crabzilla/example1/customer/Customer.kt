package crabzilla.example1.customer

import crabzilla.Aggregate
import crabzilla.DomainEvent
import crabzilla.example1.SampleInternalService

data class Customer(override val id: CustomerId? = null,
                    val name: String? = null,
                    val isActive: Boolean? = false,
                    val reason: String? = null,
                    val sampleInternalService: SampleInternalService? = null) : Aggregate {

  internal fun create(id: CustomerId, name: String): List<DomainEvent> {
    require(this.id == null, { "customer already created" })
    return events(CustomerCreated(id, name))
  }

  internal fun activate(reason: String): List<DomainEvent> {
    return events(CustomerActivated(reason, sampleInternalService!!.now()))
  }

  internal fun deactivate(reason: String): List<DomainEvent> {
    return events(CustomerDeactivated(reason, sampleInternalService!!.now()))
  }

}