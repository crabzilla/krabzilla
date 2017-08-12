package crabzilla.example1

import java.time.Instant
import java.util.*

class SampleInternalServiceImpl : SampleInternalService {

  override fun uuid(): UUID {
    return UUID.randomUUID()
  }

  override fun now(): Instant {
    return Instant.now()
  }

}
