package crabzilla.example1;

import crabzilla.vertx.BoundedContextComponentsFactory;
import crabzilla.vertx.events.projection.EventProjector;
import crabzilla.vertx.events.projection.ProjectionData;
import crabzilla.vertx.events.projection.ProjectionRepository;
import example1.readmodel.CustomerSummaryDao;
import io.vertx.ext.jdbc.JDBCClient;
import org.jdbi.v3.core.Jdbi;

import javax.inject.Inject;
import java.util.List;
import java.util.function.BiFunction;

class Example1ComponentsFactory implements BoundedContextComponentsFactory {

  private final Jdbi jdbi;
  private final JDBCClient jdbcClient;

  @Inject
  public Example1ComponentsFactory(Jdbi jooq, JDBCClient jdbcClient) {
    this.jdbi = jooq;
    this.jdbcClient = jdbcClient;
  }

  @Override
  public EventProjector eventsProjector() {
    return new Example1EventProjector("example1", CustomerSummaryDao.class, jdbi) ;
  }

  @Override
  public BiFunction<Long, Integer, List<ProjectionData>> projectionRepository() {
    return new ProjectionRepository(jdbcClient);
  }

}
