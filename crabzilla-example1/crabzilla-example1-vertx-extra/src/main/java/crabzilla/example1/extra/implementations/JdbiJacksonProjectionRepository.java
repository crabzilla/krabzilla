package crabzilla.example1.extra.implementations;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import crabzilla.model.Event;
import crabzilla.model.ProjectionData;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.function.BiFunction;

@Slf4j
public class JdbiJacksonProjectionRepository implements BiFunction<Long, Integer, List<ProjectionData>> {

  static final String UOW_ID = "uow_id";
  static final String UOW_EVENTS = "uow_events";
  static final String AR_ID = "ar_id";
  static final String UOW_SEQ_NUMBER = "uow_seq_number";

  private final ObjectMapper mapper;
  private final DBI dbi;

  private final TypeReference<List<Event>> eventsListTpe =  new TypeReference<List<Event>>() {};

  public JdbiJacksonProjectionRepository(@NonNull final ObjectMapper mapper, @NonNull final DBI dbi) {
    this.mapper = mapper;
    this.dbi = dbi;
  }

 @Override
  public List<ProjectionData> apply(Long sinceUowSequence, Integer maxResultSize) {

    log.info("will load a maximum of {} units unitOfWork work since sequence {}", maxResultSize, sinceUowSequence);

    final List<ProjectionData> projectionDataList = dbi
      .withHandle(new HandleCallback<List<ProjectionData>>() {

        final String sql = String.format("select uow_id, uow_seq_number, ar_id, uow_events " +
                        "from units_of_work where uow_seq_number > %d order by uow_seq_number limit %d",
                sinceUowSequence, maxResultSize);

        public List<ProjectionData> withHandle(Handle h) {
          return h.createQuery(sql)
                  .bind(UOW_SEQ_NUMBER, sinceUowSequence)
                  .map(new ProjectionDataMapper())
                  .list();
        }
      }
    );

    log.info("Found {} units of work since sequence {}", projectionDataList.size(), sinceUowSequence);
    return projectionDataList;

  }

  class ProjectionDataMapper implements ResultSetMapper<ProjectionData> {
    @Override
    @SneakyThrows
    public ProjectionData map(int i, ResultSet resultSet, StatementContext statementContext) throws SQLException {
      final List<Event> events = mapper.readerFor(eventsListTpe).readValue(resultSet.getString(UOW_EVENTS));
      return new ProjectionData(resultSet.getString(UOW_ID),
              resultSet.getLong(UOW_SEQ_NUMBER),
              resultSet.getString(AR_ID),
              events
      );
    }
  }

}