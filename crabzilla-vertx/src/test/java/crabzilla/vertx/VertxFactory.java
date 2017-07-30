package crabzilla.vertx;

import com.fasterxml.jackson.databind.ObjectMapper;
import crabzilla.Command;
import crabzilla.DomainEvent;
import crabzilla.EntityId;
import crabzilla.EntityUnitOfWork;
import crabzilla.vertx.util.codecs.JacksonGenericCodec;
import crabzilla.vertx.command.CommandExecution;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import lombok.val;

public class VertxFactory {

  public Vertx vertx() {

    val vertx = Vertx.vertx();

    val mapper = Json.mapper;
    mapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
    mapper.findAndRegisterModules();

    vertx.eventBus().registerDefaultCodec(CommandExecution.class,
            new JacksonGenericCodec<>(mapper, CommandExecution.class));

    vertx.eventBus().registerDefaultCodec(EntityId.class,
            new JacksonGenericCodec<>(mapper, EntityId.class));

    vertx.eventBus().registerDefaultCodec(Command.class,
            new JacksonGenericCodec<>(mapper, Command.class));

    vertx.eventBus().registerDefaultCodec(DomainEvent.class,
            new JacksonGenericCodec<>(mapper, DomainEvent.class));

    vertx.eventBus().registerDefaultCodec(EntityUnitOfWork.class,
            new JacksonGenericCodec<>(mapper, EntityUnitOfWork.class));

    return vertx;
  }

}
