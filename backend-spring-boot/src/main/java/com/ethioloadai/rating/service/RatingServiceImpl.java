package com.ethioloadai.rating.service;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.trip.exception.TripNotFoundException;
import com.ethioloadai.rating.dto.RatingResponse;
import com.ethioloadai.rating.dto.SubmitRatingRequest;
import com.ethioloadai.rating.entity.Rating;
import com.ethioloadai.rating.exception.RatingException;
import com.ethioloadai.rating.repository.RatingRepository;
import com.ethioloadai.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RatingServiceImpl implements RatingService {

    private static final Logger log = LoggerFactory.getLogger(RatingServiceImpl.class);

    private static final Set<Booking.BookingStatus> RATABLE_STATUSES =
            Set.of(Booking.BookingStatus.DELIVERED, Booking.BookingStatus.COMPLETED,
                   Booking.BookingStatus.CONFIRMED);

    private final RatingRepository ratingRepo;
    private final BookingRepository bookingRepo;

    @Override
    @Transactional
    public RatingResponse submitRating(SubmitRatingRequest request, User caller) {
        log.info("submitRating: bookingId={} callerId={} rating={}", request.bookingId(), caller.getId(), request.rating());

        Booking booking = bookingRepo.findById(request.bookingId())
                .orElseThrow(() -> TripNotFoundException.withId(request.bookingId()));

        // 1. Booking must be in a ratable state
        if (!RATABLE_STATUSES.contains(booking.getBookingStatus())) {
            throw RatingException.bookingNotCompleted(request.bookingId());
        }

        // 2. Caller must be the shipper or driver on this booking
        boolean isShipper = booking.getCargoRequest().getShipper().getId().equals(caller.getId());
        boolean isDriver  = booking.getDriver().getId().equals(caller.getId());
        if (!isShipper && !isDriver) {
            throw new RatingException.AuthorizationException(
                    "Only the booking's shipper or driver may submit a rating");
        }

        // 3. Idempotency: one rating per rater per booking
        if (ratingRepo.existsByBooking_IdAndRater_Id(request.bookingId(), caller.getId())) {
            throw new RatingException.AlreadyRatedException(request.bookingId());
        }

        Rating rating = Rating.builder()
                .booking(booking)
                .shipper(booking.getCargoRequest().getShipper())
                .driver(booking.getDriver())
                .rater(caller)
                .rating(request.rating())
                .feedback(request.feedback())
                .build();

        rating = ratingRepo.save(rating);
        log.info("submitRating: rating {} saved for booking {} by {}", rating.getId(), request.bookingId(), caller.getId());
        return RatingResponse.from(rating);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RatingResponse> getRatingsForBooking(Long bookingId, User caller) {
        Booking booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> TripNotFoundException.withId(bookingId));

        // Caller must be shipper, driver, or admin
        boolean isShipper = booking.getCargoRequest().getShipper().getId().equals(caller.getId());
        boolean isDriver  = booking.getDriver().getId().equals(caller.getId());
        boolean isAdmin   = caller.getRole() == User.Role.ADMIN;
        if (!isShipper && !isDriver && !isAdmin) {
            throw new RatingException.AuthorizationException("Not authorized to view ratings for this booking");
        }

        return ratingRepo.findByBooking_Id(bookingId).stream()
                .map(RatingResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RatingResponse> getRatingsForDriver(Long driverId, User caller) {
        // Driver can see their own ratings; admin can see any; others see public profile ratings
        return ratingRepo.findByDriver_Id(driverId).stream()
                .map(RatingResponse::from)
                .collect(Collectors.toList());
    }
}
