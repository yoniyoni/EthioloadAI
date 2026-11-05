package com.ethioloadai.booking.repository;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.carrier.dispatch.entity.DispatchAssignment;
import com.ethioloadai.carrier.offer.entity.CarrierOffer;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.user.entity.User;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Collection;

public final class BookingVisibilitySpecifications {

    private BookingVisibilitySpecifications() {
    }

    public static Specification<Booking> callerScope(User user, Collection<Long> companyIds) {
        return (root, query, builder) -> switch (user.getRole()) {
            case ADMIN -> builder.conjunction();
            case DRIVER -> builder.equal(root.get("driver").get("id"), user.getId());
            case SHIPPER -> builder.equal(
                    root.get("cargoRequest").get("shipper").get("id"), user.getId());
            case FLEET_OWNER -> carrierScope(root, query, builder, companyIds);
        };
    }

    public static Specification<Booking> companyScope(Long companyId) {
        return (root, query, builder) ->
                carrierScope(root, query, builder, java.util.List.of(companyId));
    }

    public static Specification<Booking> withStatus(Booking.BookingStatus status) {
        return (root, query, builder) -> builder.equal(root.get("bookingStatus"), status);
    }

    public static Specification<Booking> withinHistoryWindow(
            long historyDays, LocalDateTime now) {
        return (root, query, builder) -> {
            Join<Booking, Freight> freight = root.join("cargoRequest");
            Join<Booking, Trip> trip = root.join("trip", JoinType.LEFT);
            Path<Booking.BookingStatus> bookingStatus = root.get("bookingStatus");
            Path<Freight.FreightStatus> freightStatus = freight.get("status");
            Path<Trip.TripStatus> tripStatus = trip.get("tripStatus");
            Predicate nonTerminalBooking = builder.and(
                    builder.notEqual(bookingStatus, Booking.BookingStatus.CANCELLED),
                    builder.notEqual(bookingStatus, Booking.BookingStatus.COMPLETED));
            Predicate nonTerminalFreight = builder.and(
                    builder.notEqual(freightStatus, Freight.FreightStatus.CANCELLED),
                    builder.notEqual(freightStatus, Freight.FreightStatus.COMPLETED));
            Predicate noCompletedTrip = builder.or(
                    trip.get("id").isNull(),
                    builder.notEqual(tripStatus, Trip.TripStatus.COMPLETED));
            Predicate noCompletedTripWithoutTimestamp = builder.or(
                    noCompletedTrip,
                    trip.get("endTime").isNotNull());

            Predicate active = builder.and(
                    nonTerminalBooking,
                    nonTerminalFreight,
                    noCompletedTrip);
            if (historyDays <= 0) {
                return active;
            }

            LocalDateTime cutoff = now.minusDays(historyDays);
            Predicate freightCancelled = builder.equal(freightStatus, Freight.FreightStatus.CANCELLED);
            Predicate cancelledBooking = builder.and(
                    builder.equal(bookingStatus, Booking.BookingStatus.CANCELLED),
                    noCompletedTrip,
                    withinRange(builder, root.get("updatedAt"), cutoff, now));
            Predicate cancelledFreight = builder.and(
                    freightCancelled,
                    withinRange(builder, freight.get("updatedAt"), cutoff, now));
            Predicate completedTrip = builder.equal(tripStatus, Trip.TripStatus.COMPLETED);
            Predicate completedTripWithinWindow = builder.and(
                    completedTrip,
                    withinRange(builder, trip.get("endTime"), cutoff, now));
            Predicate completedBookingWithinWindow = builder.and(
                    builder.equal(bookingStatus, Booking.BookingStatus.COMPLETED),
                    noCompletedTrip,
                    withinRange(builder, root.get("updatedAt"), cutoff, now));
            Predicate completedFreightWithinWindow = builder.and(
                    builder.equal(freightStatus, Freight.FreightStatus.COMPLETED),
                    nonTerminalBooking,
                    builder.notEqual(tripStatus, Trip.TripStatus.COMPLETED),
                    withinRange(builder, freight.get("updatedAt"), cutoff, now));
            return builder.and(noCompletedTripWithoutTimestamp, builder.or(
                    active,
                    cancelledFreight,
                    builder.and(builder.notEqual(freightStatus, Freight.FreightStatus.CANCELLED),
                            cancelledBooking),
                    builder.and(builder.notEqual(freightStatus, Freight.FreightStatus.CANCELLED),
                            completedTripWithinWindow),
                    builder.and(builder.notEqual(freightStatus, Freight.FreightStatus.CANCELLED),
                            completedBookingWithinWindow),
                    builder.and(builder.notEqual(freightStatus, Freight.FreightStatus.CANCELLED),
                            completedFreightWithinWindow)));
        };
    }

    private static Predicate carrierScope(
            Root<Booking> root,
            CriteriaQuery<?> query,
            CriteriaBuilder builder,
            Collection<Long> companyIds) {
        if (companyIds == null || companyIds.isEmpty()) {
            return builder.disjunction();
        }

        Predicate directCompanyMatch = root.get("carrierCompanyId").in(companyIds);

        Subquery<Long> acceptedOffer = query.subquery(Long.class);
        Root<CarrierOffer> offer = acceptedOffer.from(CarrierOffer.class);
        acceptedOffer.select(offer.get("id"));
        acceptedOffer.where(
                builder.equal(offer.get("id"), root.get("carrierOfferId")),
                builder.equal(offer.get("status"), CarrierOffer.OfferStatus.ACCEPTED),
                offer.get("carrierCompany").get("id").in(companyIds));

        Subquery<Long> dispatch = query.subquery(Long.class);
        Root<DispatchAssignment> assignment = dispatch.from(DispatchAssignment.class);
        dispatch.select(assignment.get("id"));
        dispatch.where(
                builder.equal(assignment.get("id"), root.get("dispatchAssignmentId")),
                assignment.get("carrierCompanyId").in(companyIds));

        Predicate legacyOfferMatch = builder.exists(acceptedOffer);
        Predicate legacyDispatchMatch = builder.and(
                root.get("carrierOfferId").isNull(),
                builder.exists(dispatch));
        return builder.or(
                directCompanyMatch,
                builder.and(
                        root.get("carrierCompanyId").isNull(),
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
