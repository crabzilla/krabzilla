package crabzilla.vertx.repositories;

// what: sagas monitoring events may emit/schedule new commands to itself/other aggregate roots
// how: events implementing CommandSchedulingEvent. This class will retrieve these scheduled commands

//public interface SchedulingRepository {
//
//  void schedule(CommandSchedulingEvent scheduling);
//
//  List<CommandSchedulingEvent> listAllBefore(Instant instant, int seconds);
//
//}
