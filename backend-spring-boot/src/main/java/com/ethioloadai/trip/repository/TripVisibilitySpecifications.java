package com.ethioloadai.trip.repository;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.carrier.dispatch.entity.DispatchAssignment;
import com.ethioloadai.carrier.offer.entity.CarrierOffer;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.user.entity.User;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Collection;

public final class TripVisibilitySpecifications {

    private TripVisibilitySpecifications() {
    }

    public static Specification<Trip> callerScope(User user, Collection<Long> companyIds) {
        return (root, query, builder) -> switch (user.getRole()) {
            case ADMIN -> builder.conjunction();
            case DRIVER -> builder.equal(root.get("booking").get("driver").get("id"), user.getId());
            case SHIPPER -> builder.equal(
                    root.get("booking").get("cargoRequest").get("shipper").get("id"), user.getId());
            case FLEET_OWNER -> carrierScope(root, query, builder, companyIds);
        };
    }

    public static Specification<Trip> driverScope(Long driverId) {
        return (root, query, builder) ->
                builder.equal(root.get("booking").get("driver").get("id"), driverId);
    }

    public static Specification<Trip> carrierScope(Collection<Long> companyIds) {
        return (root, query, builder) ->
                carrierScope(root, query, builder, companyIds);
    }

    public static Specification<Trip> companyScope(Long companyId) {
        return (root, query, builder) ->
                carrierScope(root, query, builder, java.util.List.of(companyId));
    }

    public static Specification<Trip> withinHistoryWindow(
            long historyDays, LocalDateTime now) {
        return (root, query, builder) -> {
            Join<Trip, Booking> booking = root.join("booking");
            Path<Freight.FreightStatus> freightStatus = booking.get("cargoRequest").get("status");
            Path<LocalDateTime> freightUpdatedAt = booking.get("cargoRequest").get("updatedAt");
            Path<Booking.BookingStatus> bookingStatus = booking.get("bookingStatus");
            Path<LocalDateTime> bookingUpdatedAt = booking.get("updatedAt");
            Path<Trip.TripStatus> tripStatus = root.get("tripStatus");
            Predicate nonTerminalBooking = builder.and(
                    builder.notEqual(bookingStatus, Booking.BookingStatus.CANCELLED),
                    builder.notEqual(bookingStatus, Booking.BookingStatus.COMPLETED));
            Predicate completedTripTimestampKnown = builder.or(
                    builder.notEqual(tripStatus, Trip.TripStatus.COMPLETED),
                    root.get("endTime").isNotNull());

            Predicate active = builder.and(
                    builder.notEqual(freightStatus, Freight.FreightStatus.CANCELLED),
                    builder.notEqual(freightStatus, Freight.FreightStatus.COMPLETED),
                    builder.notEqual(tripStatus, Trip.TripStatus.COMPLETED),
                    nonTerminalBooking);
            if (historyDays <= 0) {
                return active;
            }

            LocalDateTime cutoff = now.minusDays(historyDays);
            Predicate freightCancelled = builder.equal(
                    freightStatus, Freight.FreightStatus.CANCELLED);
            Predicate cancellationVisible = builder.and(
                    freightCancelled,
                    withinRange(builder, freightUpdatedAt, cutoff, now));

            Path<LocalDateTime> tripEndTime = root.get("endTime");
            Predicate completedTripVisible = builder.and(
                    builder.notEqual(freightStatus, Freight.FreightStatus.CANCELLED),
                    builder.equal(tripStatus, Trip.TripStatus.COMPLETED),
                    withinRange(builder, tripEndTime, cutoff, now));
            Predicate cancelledBookingVisible = builder.and(
                    builder.notEqual(freightStatus, Freight.FreightStatus.CANCELLED),
                    builder.notEqual(tripStatus, Trip.TripStatus.COMPLETED),
                    builder.equal(bookingStatus, Booking.BookingStatus.CANCELLED),
                    withinRange(builder, bookingUpdatedAt, cutoff, now));
            Predicate completedBookingVisible = builder.and(
                    builder.notEqual(freightStatus, Freight.FreightStatus.CANCELLED),
                    builder.equal(bookingStatus, Booking.BookingStatus.COMPLETED),
                    builder.notEqual(tripStatus, Trip.TripStatus.COMPLETED),
                    withinRange(builder, bookingUpdatedAt, cutoff, now));
            Predicate completedFreightVisible = builder.and(
                    builder.notEqual(freightStatus, Freight.FreightStatus.CANCELLED),
                    builder.equal(freightStatus, Freight.FreightStatus.COMPLETED),
                    nonTerminalBooking,
                    builder.notEqual(tripStatus, Trip.TripStatus.COMPLETED),
                    withinRange(builder, freightUpdatedAt, cutoff, now));

            return builder.and(completedTripTimestampKnown, builder.or(
                    active,
                    cancellationVisible,
                    completedTripVisible,
                    cancelledBookingVisible,
                    completedBookingVisible,
                    completedFreightVisible));
        };
    }

    public static Specification<Trip> withStatus(Trip.TripStatus status) {
        return (root, query, builder) -> builder.equal(root.get("tripStatus"), status);
    }

    public static Specification<Trip> withStatuses(Collection<Trip.TripStatus> statuses) {
        return (root, query, builder) -> root.get("tripStatus").in(statuses);
    }

    public static Specification<Trip> withType(Trip.TripType type) {
        return (root, query, builder) -> builder.equal(root.get("tripType"), type);
    }

    public static Specification<Trip> startedBetween(
            LocalDateTime start, LocalDateTime end) {
        return (root, query, builder) ->
                builder.between(root.get("startTime"), start, end);
    }

    public static Specification<Trip> startedBefore(LocalDateTime timestamp) {
        return (root, query, builder) -> builder.and(
                builder.equal(root.get("tripStatus"), Trip.TripStatus.ONGOING),
                builder.lessThan(root.get("startTime"), timestamp));
    }

    private static Predicate carrierScope(
            Root<Trip> root,
            CriteriaQuery<?> query,
            CriteriaBuilder builder,
            Collection<Long> companyIds) {
        if (companyIds == null || companyIds.isEmpty()) {
            return builder.disjunction();
        }

        Join<Trip, Booking> booking = root.join("booking");
        Path<Long> bookingCompanyId = booking.get("carrierCompanyId");
        Predicate directCompanyMatch = bookingCompanyId.in(companyIds);

        Subquery<Long> acceptedOffer = query.subquery(Long.class);
        Root<CarrierOffer> offer = acceptedOffer.from(CarrierOffer.class);
        acceptedOffer.select(offer.get("id"));
        acceptedOffer.where(
                builder.equal(offer.get("id"), booking.get("carrierOfferId")),
                builder.equal(offer.get("status"), CarrierOffer.OfferStatus.ACCEPTED),
                offer.get("carrierCompany").get("id").in(companyIds));

        Subquery<Long> dispatch = query.subquery(Long.class);
        Root<DispatchAssignment> assignment = dispatch.from(DispatchAssignment.class);
        dispatch.select(assignment.get("id"));
        dispatch.where(
                builder.equal(assignment.get("id"), booking.get("dispatchAssignmentId")),
                assignment.get("carrierCompanyId").in(companyIds));

        Predicate legacyOfferMatch = builder.exists(acceptedOffer);
        Predicate legacyDispatchMatch = builder.and(
                booking.get("carrierOfferId").isNull(),
                builder.exists(dispatch));
        return builder.or(
                directCompanyMatch,
                builder.and(
                        bookingCompanyId.isNull(),
                        builder.or(legacyOfferMatch, legacyDispatchMatch)));
    }

    private static Predicate withinRange(
            CriteriaBuilder builder,
            Path<LocalDateTime> timestamp,
            LocalDateTime cutoff,
            LocalDateTime now) {
        return builder.and(
                timestamp.isNotNull(),
                builder.greaterThanOrEqualTo(timestamp, cutoff),
                builder.lessThanOrEqualTo(timestamp, now));
    }
}
