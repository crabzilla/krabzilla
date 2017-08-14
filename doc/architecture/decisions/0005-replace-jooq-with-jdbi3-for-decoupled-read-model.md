# 5. replace jooq with jdbi3 for decoupled read model

Date: 2017-08-07

## Status

Accepted

## Context

Any read model should be expressed within the example1-core module. The same for Repositories interfaces. By using [JOOQ](https://www.jooq.org/)
the example1-core module should depend on example1-database module, because it contains the read model classes generated from the database. And this module depends on JOOQ library. So at the end the example1-core would depend on JOOQ library.

## Decision

[Jdbi3](https://github.com/jdbi/jdbi) allow to express read models as plain Java or Kotlin data classes. For example: [Example1EventProjector](../../../krabzilla-example1/krabzilla-example1-service/src/main/java/crabzilla/example1/Example1EventProjector.kt).

## Consequences

With Jdbi, now the example1-core module can 100% be decoupled from any implementation aspects of repositories and services.

You can develop your model and testing it by mocking your repositories and services. Any detail about database or service
implementations can be postponed within your development process.
