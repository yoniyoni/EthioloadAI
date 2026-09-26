package com.ethioloadai.document;

import com.ethioloadai.document.entity.DriverDocument.DocumentStatus;
import com.ethioloadai.document.exception.DocumentException;
import com.ethioloadai.document.service.DocumentStateMachine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Phase 2 / Phase 12 — document state machine unit tests.
 */
class DocumentStateMachineTest {

    // ── Permitted transitions ─────────────────────────────────────────────────

    @Test
    void pending_to_approved_is_allowed() {
        assertThatNoException().isThrownBy(() ->
            DocumentStateMachine.assertTransitionAllowed(DocumentStatus.PENDING, DocumentStatus.APPROVED));
    }

    @Test
    void pending_to_rejected_is_allowed() {
        assertThatNoException().isThrownBy(() ->
            DocumentStateMachine.assertTransitionAllowed(DocumentStatus.PENDING, DocumentStatus.REJECTED));
    }

    @Test
    void approved_to_rejected_is_allowed() {
        // Admin revokes approval (fraud detected)
        assertThatNoException().isThrownBy(() ->
            DocumentStateMachine.assertTransitionAllowed(DocumentStatus.APPROVED, DocumentStatus.REJECTED));
    }

    @Test
    void rejected_to_pending_is_allowed() {
        // Re-submission after fixing issues
        assertThatNoException().isThrownBy(() ->
            DocumentStateMachine.assertTransitionAllowed(DocumentStatus.REJECTED, DocumentStatus.PENDING));
    }

    // ── Forbidden transitions ─────────────────────────────────────────────────

    @Test
    void rejected_to_approved_is_forbidden() {
        // Must go through PENDING review again
        assertThatThrownBy(() ->
            DocumentStateMachine.assertTransitionAllowed(DocumentStatus.REJECTED, DocumentStatus.APPROVED))
            .isInstanceOf(DocumentException.InvalidStatusTransitionException.class)
            .hasMessageContaining("REJECTED")
            .hasMessageContaining("APPROVED");
    }

    @Test
    void approved_to_pending_is_forbidden() {
        // Cannot un-approve without explicit rejection
        assertThatThrownBy(() ->
            DocumentStateMachine.assertTransitionAllowed(DocumentStatus.APPROVED, DocumentStatus.PENDING))
            .isInstanceOf(DocumentException.InvalidStatusTransitionException.class);
    }

    @Test
    void pending_to_pending_is_forbidden() {
        assertThatThrownBy(() ->
            DocumentStateMachine.assertTransitionAllowed(DocumentStatus.PENDING, DocumentStatus.PENDING))
            .isInstanceOf(DocumentException.InvalidStatusTransitionException.class);
    }

    // ── isTransitionAllowed ───────────────────────────────────────────────────

    @Test
    void isTransitionAllowed_returns_true_for_valid() {
        assertThat(DocumentStateMachine.isTransitionAllowed(DocumentStatus.PENDING, DocumentStatus.APPROVED)).isTrue();
        assertThat(DocumentStateMachine.isTransitionAllowed(DocumentStatus.PENDING, DocumentStatus.REJECTED)).isTrue();
        assertThat(DocumentStateMachine.isTransitionAllowed(DocumentStatus.APPROVED, DocumentStatus.REJECTED)).isTrue();
        assertThat(DocumentStateMachine.isTransitionAllowed(DocumentStatus.REJECTED, DocumentStatus.PENDING)).isTrue();
    }

    @Test
    void isTransitionAllowed_returns_false_for_invalid() {
        assertThat(DocumentStateMachine.isTransitionAllowed(DocumentStatus.REJECTED, DocumentStatus.APPROVED)).isFalse();
        assertThat(DocumentStateMachine.isTransitionAllowed(DocumentStatus.APPROVED, DocumentStatus.PENDING)).isFalse();
    }

    // ── Terminal states ───────────────────────────────────────────────────────

    @Test
    void no_status_is_terminal() {
        // In this domain, documents can always be revised or revoked
        assertThat(DocumentStateMachine.isTerminal(DocumentStatus.PENDING)).isFalse();
        assertThat(DocumentStateMachine.isTerminal(DocumentStatus.APPROVED)).isFalse();
        assertThat(DocumentStateMachine.isTerminal(DocumentStatus.REJECTED)).isFalse();
    }
}
