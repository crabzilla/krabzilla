package crabzilla.example1.aggregates

import crabzilla.DomainEvent
import crabzilla.EntityCommand
import crabzilla.EntityId
import java.time.Instant
import java.util.*

// <1>

data class CustomerId(override val stringValue: String) : EntityId

// <2>

data class CustomerCreated(val id: CustomerId, val name: String) : DomainEvent

data class CustomerActivated(val reason: String, val _when: Instant) : DomainEvent

data class CustomerDeactivated(val reason: String, val _when: Instant) : DomainEvent

// <3>

data class CreateCustomerCmd(override val commandId: UUID, override val targetId: CustomerId,
                             val name: String) : EntityCommand

data class ActivateCustomerCmd(override val commandId: UUID, override val targetId: CustomerId,
                               val reason: String) : EntityCommand

data class DeactivateCustomerCmd(override val commandId: UUID, override val targetId: CustomerId,
                                 val reason: String) : EntityCommand

data class CreateActivateCustomerCmd(override val commandId: UUID, override val targetId:
                                     CustomerId, val name: String,
                                     val reason: String) : EntityCommand

// <4>

data class UnknownCommand(override val commandId: UUID, override val targetId: CustomerId)
    : EntityCommand

