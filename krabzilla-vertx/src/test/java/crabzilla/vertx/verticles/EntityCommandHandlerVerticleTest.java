package crabzilla.vertx.verticles;

import crabzilla.*;
import crabzilla.example1.aggregates.*;
import crabzilla.vertx.SnapshotData;
import crabzilla.vertx.VertxFactory;
import crabzilla.vertx.commands.CommandExecution;
import crabzilla.vertx.commands.execution.EntityCommandHandlerVerticle;
import crabzilla.vertx.commands.execution.EntityUnitOfWorkRepository;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import kotlin.Function;
import kotlin.Lazy;
import kotlin.jvm.functions.Function1;
import lombok.val;
import net.jodah.expiringmap.ExpiringMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.stubbing.VoidAnswer2;
import org.mockito.stubbing.VoidAnswer3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static crabzilla.vertx.commands.CommandExecution.RESULT;
import static crabzilla.vertx.util.StringHelper.commandHandlerId;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.mockito.AdditionalAnswers.answerVoid;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(VertxUnitRunner.class)
public class EntityCommandHandlerVerticleTest {

  public static final String FORCED_CONCURRENCY_EXCEPTION = "FORCED CONCURRENCY EXCEPTION";
  Vertx vertx;
  CircuitBreaker circuitBreaker;
  ExpiringMap<String, Snapshot<Customer>> cache;

  final Lazy<Customer> lazyCust =  new CustomerSeedValueFn().invoke();

  @Mock
  Function1<EntityCommand, List<String>> validatorFn;
  @Mock
  EntityCommandHandlerFn<Customer> cmdHandlerFn;
  @Mock
  EntityUnitOfWorkRepository eventRepository;
  @Mock
  SnapshotUpgraderFn<Customer> snapshotUpgraderFn;

  @Before
  public void setUp(TestContext context) {

    initMocks(this);

    vertx = new VertxFactory().vertx();
    circuitBreaker = CircuitBreaker.create("cmd-handler-circuit-breaker", vertx,
            new CircuitBreakerOptions()
                    .setMaxFailures(5) // number SUCCESS failure before opening the circuit
                    .setTimeout(10000) // consider a failure if the operation does not succeed in time
                    .setFallbackOnFailure(false) // do we call the fallback on failure
                    .setResetTimeout(10000) // time spent in open state before attempting to re-try
    );

    cache = ExpiringMap.create();

    val verticle = new EntityCommandHandlerVerticle<Customer>(Customer.class, lazyCust, validatorFn, cmdHandlerFn,
            cache, snapshotUpgraderFn, eventRepository, circuitBreaker);

    vertx.deployVerticle(verticle, context.asyncAssertSuccess());

  }

  @After
  public void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void SUCCESS_scenario(TestContext tc) {

    Async async = tc.async();

    val customerId = new CustomerId("customer#1");
    val createCustomerCmd = new CreateCustomerCmd(UUID.randomUUID(), customerId, "customer");
    val initialSnapshot = new Snapshot<Customer>(lazyCust.getValue(), new Version(0));
    val expectedEvent = new CustomerCreated(createCustomerCmd.getTargetId(), "customer");
    val expectedUow = new EntityUnitOfWork(createCustomerCmd, singletonList(expectedEvent), new Version(1));

    when(validatorFn.invoke(eq(createCustomerCmd))).thenReturn(emptyList());

    doAnswer(answerVoid((VoidAnswer3<String, Version, Future<SnapshotData>>) (s, version, future) ->
            future.complete(new SnapshotData(initialSnapshot.getVersion(), new ArrayList<>()))))
            .when(eventRepository).selectAfterVersion(eq(customerId.getStringValue()),
                                                      eq(initialSnapshot.getVersion()),
                                                      any(Future.class));

    doAnswer(answerVoid((VoidAnswer2<EntityUnitOfWork, Future<Long>>) (uow, future) ->
            future.complete(1L)))
            .when(eventRepository).append(eq(expectedUow), any(Future.class));

    when(cmdHandlerFn.invoke(eq(createCustomerCmd), eq(initialSnapshot)))
            .thenReturn(new CommandHandlerResult(expectedUow, null));

    val options = new DeliveryOptions().setCodecName("Command");

    vertx.eventBus().send(commandHandlerId(Customer.class), createCustomerCmd, options, asyncResult -> {

      InOrder inOrder = inOrder(validatorFn, eventRepository, cmdHandlerFn);

      inOrder.verify(validatorFn).invoke(eq(createCustomerCmd));

      inOrder.verify(eventRepository).selectAfterVersion(eq(customerId.getStringValue()),
                                                         eq(initialSnapshot.getVersion()),
                                                         any());

      inOrder.verify(cmdHandlerFn).invoke(eq(createCustomerCmd), eq(initialSnapshot));

      inOrder.verify(eventRepository).append(eq(expectedUow), any());

      verifyNoMoreInteractions(validatorFn, eventRepository, cmdHandlerFn);

      tc.assertTrue(asyncResult.succeeded());

      val response = (CommandExecution) asyncResult.result().body();

      tc.assertEquals(RESULT.SUCCESS, response.getResult());
      tc.assertEquals(1L, response.getUowSequence());

      val resultUnitOfWork = response.getUnitOfWork();

      tc.assertEquals(expectedUow.getCommand(), resultUnitOfWork.getCommand());
      tc.assertEquals(expectedUow.getEvents(), resultUnitOfWork.getEvents());
      tc.assertEquals(expectedUow.getVersion(), resultUnitOfWork.getVersion());

      async.complete();

    });

  }

  @Test
  public void UNEXPECTED_ERROR_selectAfterVersion_scenario(TestContext tc) {

    Async async = tc.async();

    val customerId = new CustomerId("customer#1");
    val createCustomerCmd = new CreateCustomerCmd(UUID.randomUUID(), customerId, "customer");
    val initialSnapshot = new Snapshot<Customer>(lazyCust.getValue(), new Version(0));
    val expectedException = new Throwable("Expected");

    when(validatorFn.invoke(eq(createCustomerCmd))).thenReturn(emptyList());

    doAnswer(answerVoid((VoidAnswer3<String, Version, Future<SnapshotData>>) (s, version, future) ->
            future.fail(expectedException)))
            .when(eventRepository).selectAfterVersion(eq(customerId.getStringValue()),
                                                      eq(initialSnapshot.getVersion()),
                                                      any(Future.class));

    val options = new DeliveryOptions().setCodecName("Command");

    vertx.eventBus().send(commandHandlerId(Customer.class), createCustomerCmd, options, asyncResult -> {

      InOrder inOrder = inOrder(validatorFn, eventRepository);

      inOrder.verify(validatorFn).invoke(eq(createCustomerCmd));

      inOrder.verify(eventRepository).selectAfterVersion(eq(customerId.getStringValue()),
                                                         eq(initialSnapshot.getVersion()),
                                                         any());

      verifyNoMoreInteractions(validatorFn, eventRepository);

      tc.assertTrue(asyncResult.failed());

      val replyException = (ReplyException) asyncResult.cause();
      tc.assertEquals(replyException.failureCode(), 400);
      tc.assertEquals(replyException.getMessage(), expectedException.getMessage());

      async.complete();

    });

  }

  @Test
  public void UNEXPECTED_ERROR_append_scenario(TestContext tc) {

    Async async = tc.async();

    val customerId = new CustomerId("customer#1");
    val createCustomerCmd = new CreateCustomerCmd(UUID.randomUUID(), customerId, "customer");
    val initialSnapshot = new Snapshot<Customer>(lazyCust.getValue(), new Version(0));
    val expectedEvent = new CustomerCreated(createCustomerCmd.getTargetId(), "customer");
    val expectedUow = new EntityUnitOfWork(createCustomerCmd, singletonList(expectedEvent), new Version(1));
    val expectedException = new Throwable("Expected");

    when(validatorFn.invoke(eq(createCustomerCmd))).thenReturn(emptyList());

    doAnswer(answerVoid((VoidAnswer3<String, Version, Future<SnapshotData>>) (s, version, future) ->
            future.complete(new SnapshotData(initialSnapshot.getVersion(), new ArrayList<>()))))
            .when(eventRepository).selectAfterVersion(eq(customerId.getStringValue()),
                                                      eq(initialSnapshot.getVersion()),
                                                      any(Future.class));

    doAnswer(answerVoid((VoidAnswer2<EntityUnitOfWork, Future<Long>>) (uow, future) ->
            future.fail(expectedException)))
            .when(eventRepository).append(eq(expectedUow), any(Future.class));

    when(cmdHandlerFn.invoke(eq(createCustomerCmd), eq(initialSnapshot)))
            .thenReturn(new CommandHandlerResult(expectedUow, null));

    val options = new DeliveryOptions().setCodecName("Command");

    vertx.eventBus().send(commandHandlerId(Customer.class), createCustomerCmd, options, asyncResult -> {

      InOrder inOrder = inOrder(validatorFn, eventRepository, cmdHandlerFn);

      inOrder.verify(validatorFn).invoke(eq(createCustomerCmd));

      inOrder.verify(eventRepository).selectAfterVersion(eq(customerId.getStringValue()),
                                                         eq(initialSnapshot.getVersion()),
                                                         any());

      inOrder.verify(cmdHandlerFn).invoke(eq(createCustomerCmd), eq(initialSnapshot));

      inOrder.verify(eventRepository).append(eq(expectedUow), any());

      verifyNoMoreInteractions(validatorFn, eventRepository, cmdHandlerFn);

      tc.assertTrue(asyncResult.failed());

      val replyException = (ReplyException) asyncResult.cause();
      tc.assertEquals(replyException.failureCode(), 400);
      tc.assertEquals(replyException.getMessage(), expectedException.getMessage());

      async.complete();

    });

  }

  @Test
  public void CONCURRENCY_ERROR_scenario(TestContext tc) {

    Async async = tc.async();

    val customerId = new CustomerId("customer#1");
    val createCustomerCmd = new CreateCustomerCmd(UUID.randomUUID(), customerId, "customer");
    val initialSnapshot = new Snapshot<Customer>(lazyCust.getValue(), new Version(0));
    val expectedEvent = new CustomerCreated(createCustomerCmd.getTargetId(), "customer");
    val expectedUow = new EntityUnitOfWork(createCustomerCmd, singletonList(expectedEvent), new Version(1));

    when(validatorFn.invoke(eq(createCustomerCmd))).thenReturn(emptyList());

    doAnswer(answerVoid((VoidAnswer3<String, Version, Future<SnapshotData>>) (s, version, future) ->
            future.complete(new SnapshotData(initialSnapshot.getVersion(), new ArrayList<>()))))
            .when(eventRepository).selectAfterVersion(eq(customerId.getStringValue()),
            eq(initialSnapshot.getVersion()),
            any(Future.class));

    doAnswer(answerVoid((VoidAnswer2<EntityUnitOfWork, Future<Long>>) (uow, future) ->
            future.fail(new ConcurrencyConflictException(FORCED_CONCURRENCY_EXCEPTION))))
            .when(eventRepository).append(eq(expectedUow), any(Future.class));

    when(cmdHandlerFn.invoke(eq(createCustomerCmd), eq(initialSnapshot)))
            .thenReturn(new CommandHandlerResult(expectedUow, null));

    val options = new DeliveryOptions().setCodecName("Command");

    vertx.eventBus().send(commandHandlerId(Customer.class), createCustomerCmd, options, asyncResult -> {

      InOrder inOrder = inOrder(validatorFn, eventRepository, cmdHandlerFn);

      inOrder.verify(validatorFn).invoke(eq(createCustomerCmd));

      inOrder.verify(eventRepository).selectAfterVersion(eq(customerId.getStringValue()),
              eq(initialSnapshot.getVersion()),
              any());

      inOrder.verify(cmdHandlerFn).invoke(eq(createCustomerCmd), eq(initialSnapshot));

      inOrder.verify(eventRepository).append(eq(expectedUow), any());

      verifyNoMoreInteractions(validatorFn, eventRepository, cmdHandlerFn);

      tc.assertTrue(asyncResult.succeeded());

      val response = (CommandExecution) asyncResult.result().body();

      tc.assertEquals(RESULT.CONCURRENCY_ERROR, response.getResult());
      tc.assertEquals(singletonList(FORCED_CONCURRENCY_EXCEPTION), response.getConstraints());

      async.complete();

    });

  }


  @Test
  public void HANDLING_ERROR_scenario(TestContext tc) {

    Async async = tc.async();

    val customerId = new CustomerId("customer#1");
    val createCustomerCmd = new CreateCustomerCmd(UUID.randomUUID(), customerId, "customer");
    val initialSnapshot = new Snapshot<Customer>(lazyCust.getValue(), new Version(0));
    val expectedEvent = new CustomerCreated(createCustomerCmd.getTargetId(), "customer");
    val expectedUow = new EntityUnitOfWork(createCustomerCmd, singletonList(expectedEvent), new Version(1));

    when(validatorFn.invoke(eq(createCustomerCmd))).thenReturn(emptyList());

    doAnswer(answerVoid((VoidAnswer3<String, Version, Future<SnapshotData>>) (s, version, future) ->
            future.complete(new SnapshotData(initialSnapshot.getVersion(), new ArrayList<>()))))
            .when(eventRepository).selectAfterVersion(eq(customerId.getStringValue()),
            eq(initialSnapshot.getVersion()),
            any(Future.class));

    doAnswer(answerVoid((VoidAnswer2<EntityUnitOfWork, Future<Long>>) (uow, future) ->
            future.complete(1L)))
            .when(eventRepository).append(eq(expectedUow), any(Future.class));

    when(cmdHandlerFn.invoke(eq(createCustomerCmd), eq(initialSnapshot)))
            .thenReturn(new CommandHandlerResult(null, new RuntimeException("SOME ERROR WITHIN COMMAND HANDLER")));

    val options = new DeliveryOptions().setCodecName("Command");

    vertx.eventBus().send(commandHandlerId(Customer.class), createCustomerCmd, options, asyncResult -> {

      InOrder inOrder = inOrder(validatorFn, eventRepository, cmdHandlerFn);

      inOrder.verify(validatorFn).invoke(eq(createCustomerCmd));

      inOrder.verify(eventRepository).selectAfterVersion(eq(customerId.getStringValue()),
              eq(initialSnapshot.getVersion()),
              any());

      inOrder.verify(cmdHandlerFn).invoke(eq(createCustomerCmd), eq(initialSnapshot));

      verifyNoMoreInteractions(validatorFn, eventRepository, cmdHandlerFn);

      tc.assertTrue(asyncResult.succeeded());

      val response = (CommandExecution) asyncResult.result().body();

      tc.assertEquals(RESULT.HANDLING_ERROR, response.getResult());
      //  TODO inform exception message
      // tc.assertEquals(singletonList(FORCED_CONCURRENCY_EXCEPTION), response.getConstraints().get());

      async.complete();

    });

  }

  @Test
  public void VALIDATION_ERROR_scenario(TestContext tc) {

    Async async = tc.async();

    val customerId = new CustomerId("customer#1");
    val createCustomerCmd = new CreateCustomerCmd(UUID.randomUUID(), customerId, "a bad name");

    List<String> errorList = singletonList("Invalid name: a bad name");
    when(validatorFn.invoke(eq(createCustomerCmd))).thenReturn(errorList);

    val options = new DeliveryOptions().setCodecName("Command");

    vertx.eventBus().send(commandHandlerId(Customer.class), createCustomerCmd, options, asyncResult -> {

      verify(validatorFn).invoke(eq(createCustomerCmd));

      verifyNoMoreInteractions(validatorFn, cmdHandlerFn, eventRepository);

      tc.assertTrue(asyncResult.succeeded());

      val response = (CommandExecution) asyncResult.result().body();

      tc.assertEquals(RESULT.VALIDATION_ERROR, response.getResult());

      tc.assertEquals(asList("Invalid name: a bad name"), response.getConstraints());

      async.complete();

    });

  }


  @Test
  public void UNKNOWN_COMMAND_scenario(TestContext tc) {

    Async async = tc.async();

    val customerId = new CustomerId("customer#1");
    val createCustomerCmd = new UnknownCommand(UUID.randomUUID(), customerId);
    val initialSnapshot = new Snapshot<Customer>(lazyCust.getValue(), new Version(0));

    when(validatorFn.invoke(eq(createCustomerCmd))).thenReturn(emptyList());

    doAnswer(answerVoid((VoidAnswer3<String, Version, Future<SnapshotData>>) (s, version, future) ->
            future.complete(new SnapshotData(initialSnapshot.getVersion(), new ArrayList<>()))))
            .when(eventRepository).selectAfterVersion(eq(customerId.getStringValue()),
            eq(initialSnapshot.getVersion()),
            any(Future.class));

    when(cmdHandlerFn.invoke(eq(createCustomerCmd), eq(initialSnapshot)))
            .thenReturn(new CommandHandlerResult(null, new UnknownCommandException("for command UnknownCommand")));

    val options = new DeliveryOptions().setCodecName("Command");

    vertx.eventBus().send(commandHandlerId(Customer.class), createCustomerCmd, options, asyncResult -> {

      InOrder inOrder = inOrder(validatorFn, eventRepository, cmdHandlerFn);

      inOrder.verify(validatorFn).invoke(eq(createCustomerCmd));

      inOrder.verify(eventRepository).selectAfterVersion(eq(customerId.getStringValue()),
              eq(initialSnapshot.getVersion()),
              any());

      inOrder.verify(cmdHandlerFn).invoke(eq(createCustomerCmd), eq(initialSnapshot));

      verifyNoMoreInteractions(validatorFn, cmdHandlerFn, eventRepository);

      tc.assertTrue(asyncResult.succeeded());

      val response = (CommandExecution) asyncResult.result().body();

      tc.assertEquals(RESULT.UNKNOWN_COMMAND, response.getResult());

      async.complete();

    });

  }


}