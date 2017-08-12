package crabzilla.example1;

import com.google.inject.Guice;
import crabzilla.EntityUnitOfWork;
import crabzilla.Version;
import crabzilla.example1.customer.CreateCustomer;
import crabzilla.example1.customer.Customer;
import crabzilla.example1.customer.CustomerCreated;
import crabzilla.example1.customer.CustomerId;
import crabzilla.vertx.entity.EntityCommandExecution;
import crabzilla.vertx.entity.projection.EventsProjectionVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.logging.SLF4JLogDelegateFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jdbi.v3.core.Jdbi;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static crabzilla.vertx.util.StringHelper.entityId;
import static io.vertx.core.logging.LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME;
import static java.lang.System.setProperty;
import static java.util.Collections.singletonList;

@RunWith(VertxUnitRunner.class)
@Slf4j
public class Example1AcceptanceIT {

  private Vertx vertx;
  private Integer port = 8080;

  @Inject
  Map<String, Verticle> aggregateRootVerticles;

  @Inject
  EventsProjectionVerticle projectionVerticle;

  @Inject
  Jdbi jdbi;

  @Before
  public void setUp(TestContext context) throws IOException {

    vertx = Vertx.vertx();

    // Let's configure the verticle to listen on the 'test' port (randomly picked).
    // We create deployment options and set the _configuration_ json object:
//    ServerSocket socket = new ServerSocket(0);
//    port = socket.getLocalPort();
//    socket.close();

    setProperty (LOGGER_DELEGATE_FACTORY_CLASS_NAME, SLF4JLogDelegateFactory.class.getName ());
    LoggerFactory.getLogger (LoggerFactory.class); // Required for Logback to work in Vertx

    Guice.createInjector(new Example1Module(vertx)).injectMembers(this);

    DeploymentOptions options = new DeploymentOptions()
            .setConfig(new JsonObject().put("http.port", port)
            );

    for (Map.Entry<String,Verticle> v: this.aggregateRootVerticles.entrySet()) {
      vertx.deployVerticle(v.getValue(), options, context.asyncAssertSuccess());
    }

    vertx.deployVerticle(this.projectionVerticle, options, context.asyncAssertSuccess());

    val h = jdbi.open();
    h.createScript("DELETE FROM units_of_work").execute();
    h.createScript("DELETE FROM customer_summary").execute();
    h.commit();
  }

  /**
   * This method, called after our test, just cleanup everything by closing the vert.x instance
   *
   * @param context the test context
   */
  @After
  public void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  // tag::create_customer_test[]

  @Test
  public void create_customer(TestContext context) {

    // This test is asynchronous, so get an async handler to inform the test when we are done.
    final Async async = context.async();

    val customerId = new CustomerId(UUID.randomUUID().toString());
    val createCustomerCmd = new CreateCustomer(UUID.randomUUID(), customerId, "customer-test");
    val expectedEvent = new CustomerCreated(createCustomerCmd.getTargetId(), "customer-test");
    val expectedUow = new EntityUnitOfWork(createCustomerCmd, singletonList(expectedEvent), new Version(1));

    val json = Json.encodePrettily(createCustomerCmd);

    vertx.createHttpClient().put(port, "localhost", "/" + entityId(Customer.class) + "/commands")
      .putHeader("content-type", "application/json")
      .putHeader("content-length", Integer.toString(json.length()))
      .handler(response -> {
        context.assertEquals(response.statusCode(), 201);
        context.assertTrue(response.headers().get("content-type").contains("application/json"));
        response.bodyHandler(body -> {
          val cmdExec = Json.decodeValue(body.toString(), EntityCommandExecution.class);
          context.assertEquals(cmdExec.getUnitOfWork().targetId(), expectedUow.targetId());
          context.assertEquals(cmdExec.getUnitOfWork().getCommand(), expectedUow.getCommand());
          context.assertEquals(cmdExec.getUnitOfWork().getEvents(), expectedUow.getEvents());
          context.assertEquals(cmdExec.getUnitOfWork().getVersion(), expectedUow.getVersion());
          async.complete();
        });
      })
      .write(json)
      .end();

  }

  // tag::create_customer_test[]

}
