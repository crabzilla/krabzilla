package crabzilla.vertx.entity.projection;

import crabzilla.EntityUnitOfWork;
import crabzilla.Version;
import crabzilla.example1.CustomerSummary;
import crabzilla.example1.customer.CreateCustomer;
import crabzilla.example1.customer.CustomerCreated;
import crabzilla.example1.customer.CustomerId;
import crabzilla.vertx.VertxFactory;
import crabzilla.example1.CustomerSummaryDao;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import lombok.val;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;

import java.util.UUID;

import static crabzilla.vertx.util.StringHelper.eventsHandlerId;
import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(VertxUnitRunner.class)
public class EventsProjectionVerticleTest {

  Vertx vertx;
  CircuitBreaker circuitBreaker;

  EventProjector<CustomerSummaryDao> eventProjector;
  @Mock
  CustomerSummaryDao dao;
  @Mock
  Handle handle;
  @Mock
  Jdbi jdbi;

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

    eventProjector = new EventsProjector4Test("crabzilla/example1", CustomerSummaryDao.class, jdbi);

    val verticle = new EventsProjectionVerticle(eventProjector, circuitBreaker);

    vertx.deployVerticle(verticle, context.asyncAssertSuccess());

  }

  @After
  public void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void must_call_events_projector(TestContext tc) {

    Async async = tc.async();

    val name = "customer";

    val customerId = new CustomerId("customer#1");
    val createCustomerCmd = new CreateCustomer(UUID.randomUUID(), customerId, name);
    val expectedEvent = new CustomerCreated(createCustomerCmd.getTargetId(), name);
    val expectedUow = new EntityUnitOfWork(createCustomerCmd, singletonList(expectedEvent), new Version(1));
    val uowSequence = 1L;
    val options = new DeliveryOptions().setCodecName(EntityUnitOfWork.class.getSimpleName())
                                       .addHeader("uowSequence", uowSequence + "");

    val projectionData =
            new ProjectionData(expectedUow.getId(), uowSequence,
                    expectedUow.targetId().getStringValue(), expectedUow.getEvents());

    when(jdbi.open()).thenReturn(handle);
    when(handle.attach(any())).thenReturn(dao);

    vertx.eventBus().send(eventsHandlerId("crabzilla/example1"), expectedUow, options, asyncResult -> {

      val custSummary = new CustomerSummary(customerId.getStringValue(), name, false);

      InOrder inOrder = inOrder(jdbi, handle, dao);

      inOrder.verify(jdbi).open();
      inOrder.verify(handle).attach(any());
      inOrder.verify(dao).insert(eq(custSummary));
      inOrder.verify(handle).commit();

      verifyNoMoreInteractions(dao, jdbi, handle);

      tc.assertTrue(asyncResult.succeeded());

      async.complete();

    });

  }
}