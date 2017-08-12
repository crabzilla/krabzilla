package crabzilla.vertx.entity;

import crabzilla.Command;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import static crabzilla.vertx.util.StringHelper.*;

@Slf4j
public class EntityCommandRestVerticle<E> extends AbstractVerticle {

  final Class<E> aggregateRootClass;

  public EntityCommandRestVerticle(@NonNull Class<E> aggregateRootClass) {
    this.aggregateRootClass = aggregateRootClass;
  }

  @Override
  public void start() throws Exception {

    val router = Router.router(vertx);

    router.route(HttpMethod.PUT, "/" + entityId(aggregateRootClass) + "/commands")
          .handler(contextHandler());

    val server = vertx.createHttpServer();

    server.requestHandler(router::accept).listen(8080);
  }

  Handler<RoutingContext> contextHandler() {

    return routingContext -> {

      routingContext.request().bodyHandler(buff -> {

        val command = Json.decodeValue(new String(buff.getBytes()), Command.class);
        val httpResp = routingContext.request().response();
        val options = new DeliveryOptions().setCodecName("Command");

        vertx.<EntityCommandExecution>eventBus().send(commandHandlerId(aggregateRootClass), command, options, response -> {

          if (response.succeeded()) {
            log.info("success commands handler: {}", response);
            val result = (EntityCommandExecution) response.result().body();
            val headers = new CaseInsensitiveHeaders().add("uowSequence", result.getUowSequence().toString());
            val optionsUow = new DeliveryOptions().setCodecName("EntityUnitOfWork").setHeaders(headers);
            vertx.<String>eventBus().publish(eventsHandlerId("example1"), result.getUnitOfWork(), optionsUow);
            httpResp.end(response.result().body().toString());

          } else {
            httpResp.setStatusCode(500).end(response.cause().getMessage());
          }
        });

      });

    };
  }

}
