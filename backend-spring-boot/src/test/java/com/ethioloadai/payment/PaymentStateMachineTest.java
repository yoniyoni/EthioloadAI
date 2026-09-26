package com.ethioloadai.payment;

import com.ethioloadai.payment.entity.Payment.PaymentStatus;
import com.ethioloadai.payment.exception.PaymentException;
import com.ethioloadai.payment.service.PaymentStateMachine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Phase 4 / Phase 13 — payment state machine unit tests.
 */
class PaymentStateMachineTest {

    // ── Permitted transitions ─────────────────────────────────────────────────

    @Test
    void pending_to_paid_is_allowed() {
        assertThatNoException().isThrownBy(() ->
            PaymentStateMachine.assertTransitionAllowed(PaymentStatus.PENDING, PaymentStatus.PAID));
    }

    @Test
    void pending_to_failed_is_allowed() {
        assertThatNoException().isThrownBy(() ->
            PaymentStateMachine.assertTransitionAllowed(PaymentStatus.PENDING, PaymentStatus.FAILED));
    }

    // ── Forbidden transitions ─────────────────────────────────────────────────

    @Test
    void paid_to_any_is_forbidden() {
        for (PaymentStatus to : PaymentStatus.values()) {
            if (to != PaymentStatus.PAID) {
                assertThatThrownBy(() ->
                    PaymentStateMachine.assertTransitionAllowed(PaymentStatus.PAID, to))
                    .isInstanceOf(PaymentException.InvalidStatusTransitionException.class);
            }
        }
    }

    @Test
    void failed_to_any_is_forbidden() {
        for (PaymentStatus to : PaymentStatus.values()) {
            assertThatThrownBy(() ->
                PaymentStateMachine.assertTransitionAllowed(PaymentStatus.FAILED, to))
                .isInstanceOf(PaymentException.InvalidStatusTransitionException.class);
        }
    }

    @Test
    void paid_to_pending_is_forbidden() {
        assertThatThrownBy(() ->
            PaymentStateMachine.assertTransitionAllowed(PaymentStatus.PAID, PaymentStatus.PENDING))
            .isInstanceOf(PaymentException.InvalidStatusTransitionException.class)
            .hasMessageContaining("PAID")
            .hasMessageContaining("PENDING");
    }

    @Test
    void failed_to_paid_is_forbidden() {
        assertThatThrownBy(() ->
            PaymentStateMachine.assertTransitionAllowed(PaymentStatus.FAILED, PaymentStatus.PAID))
            .isInstanceOf(PaymentException.InvalidStatusTransitionException.class);
    }

    // ── Terminal state queries ────────────────────────────────────────────────

    @Test
    void paid_is_terminal() {
        assertThat(PaymentStateMachine.isTerminal(PaymentStatus.PAID)).isTrue();
    }

    @Test
    void failed_is_terminal() {
        assertThat(PaymentStateMachine.isTerminal(PaymentStatus.FAILED)).isTrue();
    }

    @Test
    void pending_is_not_terminal() {
        assertThat(PaymentStateMachine.isTerminal(PaymentStatus.PENDING)).isFalse();
    }

    // ── isTransitionAllowed ───────────────────────────────────────────────────

    @Test
    void isTransitionAllowed_returns_true_for_valid() {
        assertThat(PaymentStateMachine.isTransitionAllowed(PaymentStatus.PENDING, PaymentStatus.PAID)).isTrue();
        assertThat(PaymentStateMachine.isTransitionAllowed(PaymentStatus.PENDING, PaymentStatus.FAILED)).isTrue();
    }

    @Test
    void isTransitionAllowed_returns_false_for_invalid() {
        assertThat(PaymentStateMachine.isTransitionAllowed(PaymentStatus.PAID, PaymentStatus.PENDING)).isFalse();
        assertThat(PaymentStateMachine.isTransitionAllowed(PaymentStatus.FAILED, PaymentStatus.PAID)).isFalse();
    }
}
