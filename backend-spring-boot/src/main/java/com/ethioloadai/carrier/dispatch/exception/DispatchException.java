package com.ethioloadai.carrier.dispatch.exception;

import com.ethioloadai.carrier.dispatch.entity.DispatchStatus;
import com.ethioloadai.exception.BaseException;

/**
 * Phase 6 — Exceptions for the dispatch assignment domain.
 *
 * <p>All dispatch errors extend {@link BaseException} so they are handled
 * uniformly by the global exception handler.
 */
public class DispatchException extends BaseException {

    public DispatchException(String message, String code, int httpStatus) {
        super(message, code, httpStatus);
    }

    // ── Factory methods ───────────────────────────────────────────────────────

    public static DispatchException notFound(Long id) {
        return new DispatchException(
                "Dispatch assignment not found: " + id,
                "DISPATCH_NOT_FOUND", 404);
    }

    public static DispatchException noAward(Long freightId) {
        return new DispatchException(
                "Freight " + freightId + " does not have an accepted carrier award",
                "DISPATCH_NO_CARRIER_AWARD", 422);
    }

    public static DispatchException notAwardedCompany(Long freightId) {
        return new DispatchException(
                "Your carrier company did not win the award for freight " + freightId,
                "DISPATCH_COMPANY_NOT_AWARDED", 403);
    }

    public static DispatchException freightNotDispatchable(Long freightId, String reason) {
        return new DispatchException(
                "Freight " + freightId + " cannot be dispatched: " + reason,
                "DISPATCH_FREIGHT_NOT_ELIGIBLE", 422);
    }

    public static DispatchException activeDispatchExists(Long freightId) {
        return new DispatchException(
                "Freight " + freightId + " already has an active dispatch assignment",
                "DISPATCH_ALREADY_ACTIVE", 409);
    }

    public static DispatchException driverIneligible(String reason) {
        return new DispatchException(
                "Driver is not eligible for dispatch: " + reason,
                "DISPATCH_DRIVER_INELIGIBLE", 422);
    }

    public static DispatchException vehicleIneligible(String reason) {
        return new DispatchException(
                "Vehicle is not eligible for dispatch: " + reason,
                "DISPATCH_VEHICLE_INELIGIBLE", 422);
    }

    public static DispatchException crossCompanyDriver() {
        return new DispatchException(
                "Selected driver does not belong to your carrier company",
                "DISPATCH_CROSS_COMPANY_DRIVER", 403);
    }

    public static DispatchException crossCompanyVehicle() {
        return new DispatchException(
                "Selected vehicle does not belong to your carrier company",
                "DISPATCH_CROSS_COMPANY_VEHICLE", 403);
    }

    public static DispatchException wrongDriver() {
        return new DispatchException(
                "Only the assigned driver may respond to this assignment",
                "DISPATCH_WRONG_DRIVER", 403);
    }

    public static DispatchException invalidTransition(DispatchStatus current, String action) {
        return new DispatchException(
                "Cannot " + action + " a dispatch that is " + current,
                "DISPATCH_INVALID_TRANSITION", 422);
    }

    public static DispatchException agreedAmountMissing(Long offerId) {
        return new DispatchException(
                "Carrier offer " + offerId + " has no frozen agreed_amount — was it properly accepted?",
                "DISPATCH_AGREED_AMOUNT_MISSING", 422);
    }

    public static DispatchException concurrentConflict() {
        return new DispatchException(
                "Concurrent modification conflict — please retry",
                "DISPATCH_CONCURRENT_CONFLICT", 409);
    }

    public static DispatchException insufficientCapacity(double vehicleCapacity, double required) {
        return new DispatchException(
                String.format("Vehicle capacity %.2f tons is insufficient for freight requiring %.2f tons",
                        vehicleCapacity, required),
                "DISPATCH_INSUFFICIENT_CAPACITY", 422);
    }
}
