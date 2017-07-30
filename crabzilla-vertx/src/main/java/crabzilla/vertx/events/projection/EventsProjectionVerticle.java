package crabzilla.vertx.events.projection;

import crabzilla.EntityUnitOfWork;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Named;

import static crabzilla.vertx.util.StringHelper.eventsHandlerId;
import static java.util.Collections.singletonList;

@Slf4j
public class EventsProjectionVerticle extends AbstractVerticle {

  final Vertx vertx;
  final EventProjector eventProjector;
  final CircuitBreaker circuitBreaker;

  @Inject
  public EventsProjectionVerticle(@NonNull Vertx vertx,
                                  @NonNull EventProjector eventProjector,
                                  @NonNull @Named("events-projection") CircuitBreaker circuitBreaker) {
    this.vertx = vertx;
    this.eventProjector = eventProjector;
    this.circuitBreaker = circuitBreaker;
  }

  @Override
  public void start() throws Exception {

    vertx.eventBus().consumer(eventsHandlerId("example1"), msgHandler());

  }


  Handler<Message<EntityUnitOfWork>> msgHandler() {

    return (Message<EntityUnitOfWork> msg) -> {

      vertx.executeBlocking((Future<String> future) -> {

        log.info("Received ProjectionData msg {} ", msg);

        val uow = msg.body();
        val uowSequence = new Long(msg.headers().get("uowSequence"));
        val projectionData =
                new ProjectionData(uow.getId(), uowSequence,
                        uow.targetId().getStringValue(), uow.getEvents());

        circuitBreaker.fallback(throwable -> {
          log.warn("Fallback for uowHandler ");
          return "fallback";
        })

        .execute(uowHandler(projectionData))

        .setHandler(resultHandler(msg));

      }, resultHandler(msg));

    };

  }

  Handler<Future<String>> uowHandler(final ProjectionData projectionData) {

    return future -> {

      eventProjector.handle(singletonList(projectionData));

      future.complete("roger that");

    };

  }

  Handler<AsyncResult<String>> resultHandler(final Message<EntityUnitOfWork> msg) {

    return (AsyncResult<String> resultHandler) -> {

      if (resultHandler.succeeded()) {

        val resp = resultHandler.result();
        log.info("success: {}", resp);
        msg.reply(resp);

      } else {

        log.error("error cause: {}", resultHandler.cause());
        log.error("error message: {}", resultHandler.cause().getMessage());
        resultHandler.cause().printStackTrace();
        // TODO customize conform commandResult
        msg.fail(400, resultHandler.cause().getMessage());
      }

    };

  }

}

