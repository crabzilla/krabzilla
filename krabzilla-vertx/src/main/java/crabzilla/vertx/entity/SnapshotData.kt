package crabzilla.vertx.entity

import crabzilla.DomainEvent
import crabzilla.Version

import java.io.Serializable

data class SnapshotData(val version: Version, val events: List<DomainEvent>) : Serializable