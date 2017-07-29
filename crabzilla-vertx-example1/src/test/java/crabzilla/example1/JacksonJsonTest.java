package crabzilla.example1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import crabzilla.Command;
import crabzilla.EntityUnitOfWork;
import crabzilla.Version;
import crabzilla.example1.aggregates.customer.CreateCustomerCmd;
import crabzilla.example1.aggregates.customer.CustomerActivated;
import crabzilla.example1.aggregates.customer.CustomerCreated;
import crabzilla.example1.aggregates.customer.CustomerId;
import io.vertx.core.json.Json;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class JacksonJsonTest {

  ObjectMapper mapper = Json.mapper;

  @BeforeEach
  public void setup() {
    mapper.registerModule(new ParameterNamesModule())
            .registerModule(new Jdk8Module())
            .registerModule(new JavaTimeModule())
            .registerModule(new KotlinModule());
  }

  @Test
  public void one_event() throws Exception {

    val id = new CustomerId(UUID.randomUUID().toString());
    val command = new CreateCustomerCmd(UUID.randomUUID(), id, "customer1");
    val event = new CustomerCreated(id, command.getName());
    val uow1 = new EntityUnitOfWork(command, Collections.singletonList(event), new Version(1));

    val uowAsJson = mapper.writeValueAsString(uow1);

    System.out.println(mapper.writerFor(Command.class).writeValueAsString(command));
    System.out.println(uowAsJson);

    val uow2 = mapper.readValue(uowAsJson, EntityUnitOfWork.class);

    assertThat(uow2).isEqualTo(uow1);

  }

  @Test
  public void more_events() throws Exception {

    val id = new CustomerId("customer#1");
    val command = new CreateCustomerCmd(UUID.randomUUID(), id, "customer1");
    val event1 = new CustomerCreated(id, command.getName());
    val event2 = new CustomerActivated("a rgood reason", Instant.now());

    val uow1 = new EntityUnitOfWork(command, asList(event1,  event2), new Version(1));

    val uowAsJson = mapper.writeValueAsString(uow1);

    System.out.println(uowAsJson);

    val uow2 = mapper.readValue(uowAsJson, EntityUnitOfWork.class);

    assertThat(uow2).isEqualTo(uow1);

  }

}
