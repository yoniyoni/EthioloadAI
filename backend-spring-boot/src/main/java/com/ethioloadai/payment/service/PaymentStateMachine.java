package com.ethioloadai.payment.service;

import com.ethioloadai.payment.entity.Payment.PaymentStatus;
import com.ethioloadai.payment.exception.PaymentException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Strict payment state machine.
 *
 * <p>Permitted transitions (derived from the established domain — Laravel reference
 * and the payments table CHECK constraint):
 *
 * <pre>
 *   PENDING → PAID     (normal completion)
 *   PENDING → FAILED   (failure recorded — e.g. gateway rejection)
 *   PAID    → (none)   terminal
 *   FAILED  → (none)   terminal
 * </pre>
 *
 * The Laravel reference only ever transitions PENDING → PAID.
 * FAILED is present in the DB CHECK constraint and PaymentStatus enum but the
 * reference never uses it, so we treat it as a server-recorded terminal state
 * (not client-triggered).
 */
public final class PaymentStateMachine {

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED =
        new EnumMap<>(PaymentStatus.class);

    static {
        ALLOWED.put(PaymentStatus.PENDING, EnumSet.of(PaymentStatus.PAID, PaymentStatus.FAILED));
        ALLOWED.put(PaymentStatus.PAID,    EnumSet.noneOf(PaymentStatus.class));
        ALLOWED.put(PaymentStatus.FAILED,  EnumSet.noneOf(PaymentStatus.class));
    }

    private PaymentStateMachine() {}

    /**
     * Validate and enforce a transition. Throws if the transition is not permitted.
     *
     * @param from current status
     * @param to   requested status
     * @throws PaymentException.InvalidStatusTransitionException when the transition is forbidden
     */
    public static void assertTransitionAllowed(PaymentStatus from, PaymentStatus to) {
        Set<PaymentStatus> permitted = ALLOWED.getOrDefault(from, EnumSet.noneOf(PaymentStatus.class));
        if (!permitted.contains(to)) {
            throw new PaymentException.InvalidStatusTransitionException(from, to);
        }
    }

    /**
     * @return true if the status is terminal (no further transitions allowed)
     */
    public static boolean isTerminal(PaymentStatus status) {
        return ALLOWED.getOrDefault(status, EnumSet.noneOf(PaymentStatus.class)).isEmpty();
    }

    /**
     * @return true if the given transition is permitted without throwing
     */
    public static boolean isTransitionAllowed(PaymentStatus from, PaymentStatus to) {
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(PaymentStatus.class)).contains(to);
    }
}
