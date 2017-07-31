package crabzilla.example1;

import crabzilla.vertx.BoundedContextComponentsFactory;
import crabzilla.vertx.events.projection.EventProjector;
import crabzilla.vertx.events.projection.ProjectionData;
import crabzilla.vertx.events.projection.ProjectionRepository;
import io.vertx.ext.jdbc.JDBCClient;
import org.jooq.Configuration;

import javax.inject.Inject;
import java.util.List;
import java.util.function.BiFunction;

class Example1ComponentsFactory implements BoundedContextComponentsFactory {

  private final Configuration jooq;
  private final JDBCClient jdbcClient;

  @Inject
  public Example1ComponentsFactory(Configuration jooq, JDBCClient jdbcClient) {
    this.jooq = jooq;
    this.jdbcClient = jdbcClient;
  }

  @Override
  public EventProjector eventsProjector() {
    return new Example1EventProjector("example1", jooq) ;
  }

  @Override
  public BiFunction<Long, Integer, List<ProjectionData>> projectionRepository() {
    return new ProjectionRepository(jdbcClient);
  }

}
