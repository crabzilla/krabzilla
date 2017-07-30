package crabzilla.vertx.verticles;

import crabzilla.EntityUnitOfWork;
import crabzilla.Version;
import crabzilla.example1.aggregates.CreateCustomerCmd;
import crabzilla.example1.aggregates.CustomerCreated;
import crabzilla.example1.aggregates.CustomerId;
import crabzilla.vertx.EventProjector;
import crabzilla.vertx.ProjectionData;
import crabzilla.vertx.VertxFactory;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import lombok.val;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.UUID;

import static crabzilla.vertx.util.StringHelper.eventsHandlerId;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(VertxUnitRunner.class)
public class EventsProjectionVerticleTest {

  Vertx vertx;
  CircuitBreaker circuitBreaker;

  @Mock
  EventProjector eventProjector;

  @Before
  public void setUp(TestContext context) {

    initMocks(this);

    vertx = new VertxFactory().vertx();
    circuitBreaker = CircuitBreaker.create("cmd-handler-circuit-breaker", vertx,
            new CircuitBreakerOptions()
                    .setMaxFailures(5) // number SUCCESS failure before opening the circuit
                    .setTimeout(2000) // consider a failure if the operation does not succeed in time
                    .setFallbackOnFailure(true) // do we call the fallback on failure
                    .setResetTimeout(10000) // time spent in open state before attempting to re-try
    );

    val verticle = new EventsProjectionVerticle(vertx, eventProjector, circuitBreaker);

    vertx.deployVerticle(verticle, context.asyncAssertSuccess());

  }

  @After
  public void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void must_call_events_projector(TestContext tc) {

    Async async = tc.async();

    val customerId = new CustomerId("customer#1");
    val createCustomerCmd = new CreateCustomerCmd(UUID.randomUUID(), customerId, "customer");
    val expectedEvent = new CustomerCreated(createCustomerCmd.getTargetId(), "customer");
    val expectedUow = new EntityUnitOfWork(createCustomerCmd, singletonList(expectedEvent), new Version(1));
    val uowSequence = 1L;
    val options = new DeliveryOptions().setCodecName(EntityUnitOfWork.class.getSimpleName())
                                       .addHeader("uowSequence", uowSequence + "");

    val projectionData =
            new ProjectionData(expectedUow.getId(), uowSequence,
                    expectedUow.targetId().getStringValue(), expectedUow.getEvents());

    vertx.eventBus().send(eventsHandlerId("example1"), expectedUow, options, asyncResult -> {

      verify(eventProjector).handle(eq(asList(projectionData)));

      verifyNoMoreInteractions(eventProjector);

      tc.assertTrue(asyncResult.succeeded());

      async.complete();

    });

  }
}