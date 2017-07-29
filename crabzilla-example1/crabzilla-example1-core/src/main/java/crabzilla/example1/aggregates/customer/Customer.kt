package crabzilla.example1.aggregates.customer

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

    return listOf<DomainEvent>(CustomerCreated(id, name))
  }

  internal fun activate(reason: String): List<DomainEvent> {

    return listOf<DomainEvent>(CustomerActivated(reason, sampleService!!.now()))
  }

  internal fun deactivate(reason: String): List<DomainEvent> {

    return listOf<DomainEvent>(CustomerDeactivated(reason, sampleService!!.now()))
  }

}
