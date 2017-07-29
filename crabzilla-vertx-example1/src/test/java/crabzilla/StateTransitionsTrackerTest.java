package crabzilla;

import crabzilla.example1.aggregates.customer.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import java.time.Instant;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("A StateTransitionsTracker")
public class StateTransitionsTrackerTest {

  final Customer customer = new CustomerFirstInstanceFn().invoke().getValue();

  StateTracker<Customer> stateTracker;

  @BeforeEach
  void instantiate() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void can_be_instantiated() {
    new StateTracker<>(customer, new CustomerStateTransitionFn(), customer -> customer);
  }

  @Nested
  @DisplayName("when new")
  public class WhenIsNew {

    @BeforeEach
    void instantiate() {
      stateTracker = new StateTracker<>(customer, new CustomerStateTransitionFn(), customer -> customer);
    }

    @Test
    void is_empty() {
      assertThat(stateTracker.isEmpty()).isTrue();
    }

    @Test
    void has_empty_state() {
      assertThat(stateTracker.currentState()).isEqualTo(customer);
    }

    @Nested
    @DisplayName("when adding a create customer event")
    public class WhenAddingNewEvent {

      final CustomerId id = new CustomerId("c1");
      private CustomerCreated customerCreated = new CustomerCreated(id, "customer-1");
      private Customer expectedCustomer = new Customer(id, "customer-1", false, null, null);

      @BeforeEach
      void apply_create_event() {
        stateTracker.applyEvents(c -> singletonList(customerCreated));
      }

      @Test
      void has_new_state() {
        assertThat(stateTracker.currentState()).isEqualTo(expectedCustomer);
      }

      @Test
      void has_only_create_event() {
        assertThat(stateTracker.collectEvents()).contains(customerCreated);
        assertThat(stateTracker.collectEvents().size()).isEqualTo(1);
      }

      @Nested
      @DisplayName("when adding an activate customer event")
      public class WhenAddingActivateEvent {

        private CustomerActivated customerActivated = new CustomerActivated("is ok", Instant.now());
        private Customer expectedCustomer = new Customer(id, "customer-1", true,
                customerActivated.getReason(), null);

        @BeforeEach
        void apply_activate_event() {
          stateTracker.applyEvents(c -> singletonList(customerActivated));
        }

        @Test
        void has_new_state() {
          assertThat(stateTracker.currentState()).isEqualTo(expectedCustomer);
        }

        @Test
        void has_both_create_and_activated_evenst() {
          assertThat(stateTracker.collectEvents().get(0)).isEqualTo(customerCreated);
          assertThat(stateTracker.collectEvents().get(1)).isEqualTo(customerActivated);
          assertThat(stateTracker.collectEvents().size()).isEqualTo(2);
        }

      }

    }

  }

  @Nested
  @DisplayName("when adding both create and activate events")
  public class WhenAddingCreateActivateEvent {

    final String IS_OK = "is ok";

    final CustomerId id = new CustomerId("c1");
    private CustomerCreated customerCreated = new CustomerCreated(id, "customer-1");
    private CustomerActivated customerActivated = new CustomerActivated(IS_OK, Instant.now());
    private Customer expectedCustomer = new Customer(id, "customer-1", true, IS_OK, null);

    @BeforeEach
    void instantiate() {
      // given
      stateTracker = new StateTracker<>(customer, new CustomerStateTransitionFn(), customer -> customer);
      // when
      stateTracker.applyEvents(c -> asList(customerCreated, customerActivated));
    }

    // then

    @Test
    void has_new_state() {
      assertThat(stateTracker.currentState()).isEqualTo(expectedCustomer);
    }

    @Test
    void has_both_event() {
      assertThat(stateTracker.collectEvents()).contains(customerCreated);
      assertThat(stateTracker.collectEvents()).contains(customerActivated);
      assertThat(stateTracker.collectEvents().size()).isEqualTo(2);
    }

  }

}