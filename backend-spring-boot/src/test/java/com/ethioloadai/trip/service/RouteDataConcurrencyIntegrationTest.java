package com.ethioloadai.trip.service;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.entity.TripStop;
import com.ethioloadai.trip.exception.TripValidationException;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.trip.repository.TripStopRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class RouteDataConcurrencyIntegrationTest {

    @Autowired private RouteDataService routeDataService;
    @Autowired private TripRepository tripRepository;
    @Autowired private TripStopRepository tripStopRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private FreightRepository freightRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TripLifecycleService tripLifecycleService;
    @Autowired private TripStopService tripStopService;
    @Autowired private TripStatusTransitionService tripStatusTransitionService;
    @Autowired private PlatformTransactionManager transactionManager;

    private Long tripId;
    private Long bookingId;
    private Long freightId;
    private Long vehicleId;
    private Long driverId;
    private Long shipperId;

    @Test
    void simultaneousGpsAppendsPreserveBothPointsFromSameStaleSnapshot() throws Exception {
        User driver = saveUser(User.Role.DRIVER);
        User shipper = saveUser(User.Role.SHIPPER);
        Vehicle vehicle = vehicleRepository.saveAndFlush(Vehicle.builder()
                .userId(driver.getId())
                .truckType("Test truck")
                .plateNumber(unique("GPS"))
                .capacity(10.0)
                .build());
        vehicleId = vehicle.getId();

        Freight freight = freightRepository.saveAndFlush(Freight.builder()
                .shipper(shipper)
                .pickupLocation("Pickup")
                .destination("Destination")
                .materialType("General")
                .weightTons(BigDecimal.ONE)
                .budget(BigDecimal.valueOf(1000))
                .urgencyLevel("NORMAL")
                .build());
        freightId = freight.getId();

        Booking booking = bookingRepository.saveAndFlush(Booking.builder()
                .cargoRequest(freight)
                .vehicle(vehicle)
                .driver(driver)
                .bookingStatus(Booking.BookingStatus.CONFIRMED)
                .commissionFee(BigDecimal.ZERO)
                .build());
        bookingId = booking.getId();

        Trip trip = tripRepository.saveAndFlush(Trip.builder()
                .booking(booking)
                .driver(driver)
                .vehicle(vehicle)
                .startLocation("Pickup")
                .destination("Destination")
                .tripType(Trip.TripType.MULTI_STOP)
                .totalStops(2)
                .completedStops(1)
                .tripStatus(Trip.TripStatus.ONGOING)
                .build());
        tripId = trip.getId();
        tripStopRepository.saveAndFlush(TripStop.builder()
                .trip(trip)
                .stopOrder(1)
                .locationName("Completed intermediate stop")
                .stopStatus(TripStop.StopStatus.COMPLETED)
                .completedAt(LocalDateTime.now().minusMinutes(1))
                .build());

        var transactions = new TransactionTemplate(transactionManager);
        CountDownLatch snapshotsLoaded = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch firstWriteComplete = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);
        CountDownLatch secondWriteStarted = new CountDownLatch(1);
        CountDownLatch secondTransactionComplete = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> first = executor.submit(() -> appendInTransaction(
                transactions, driver, gpsPoint("9.030", "38.740", 1),
                snapshotsLoaded, start, firstWriteComplete, releaseFirstTransaction,
                null, null));
        Future<?> second = executor.submit(() -> appendInTransaction(
                transactions, driver, gpsPoint("9.031", "38.741", 2),
                snapshotsLoaded, start, firstWriteComplete, null,
                secondWriteStarted, secondTransactionComplete));

        try {
            assertThat(snapshotsLoaded.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(firstWriteComplete.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(secondWriteStarted.await(5, TimeUnit.SECONDS)).isTrue();

            // The second transaction must wait for the first row lock instead of saving
            // its stale route_data snapshot over the first committed GPS point.
            assertThat(secondTransactionComplete.await(300, TimeUnit.MILLISECONDS)).isFalse();
        } finally {
            releaseFirstTransaction.countDown();
            executor.shutdown();
        }

        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);
        executor.shutdownNow();

        var routeData = tripRepository.findById(tripId).orElseThrow().getRouteData();
        assertThat(routeData).extracting(point -> new BigDecimal(point.get("lat").toString()))
                .containsExactlyInAnyOrder(new BigDecimal("9.03"), new BigDecimal("9.031"));

        User unauthorizedUser = userRepository.findById(shipperId).orElseThrow();
        Trip currentTrip = tripRepository.findById(tripId).orElseThrow();
        assertThatThrownBy(() -> routeDataService.addGpsLocation(
                currentTrip, gpsPoint("9.032", "38.742", 3), unauthorizedUser))
                .isInstanceOf(TripValidationException.class);
        assertThat(tripRepository.findById(tripId).orElseThrow().getRouteData()).hasSize(2);
    }

    @Test
    void completionWaitsForConcurrentArrivalAndValidatesCommittedTripState() throws Exception {
        User driver = saveUser(User.Role.DRIVER);
        User shipper = saveUser(User.Role.SHIPPER);
        Vehicle vehicle = vehicleRepository.saveAndFlush(Vehicle.builder()
                .userId(driver.getId())
                .truckType("Test truck")
                .plateNumber(unique("TRIP-STATE"))
                .capacity(10.0)
                .availabilityStatus("on_trip")
                .build());
        vehicleId = vehicle.getId();

        Freight freight = freightRepository.saveAndFlush(Freight.builder()
                .shipper(shipper)
                .pickupLocation("Pickup")
                .destination("Destination")
                .materialType("General")
                .weightTons(BigDecimal.ONE)
                .budget(BigDecimal.valueOf(1000))
                .urgencyLevel("NORMAL")
                .status(Freight.FreightStatus.MATCHED)
                .matchedDriver(driver)
                .build());
        freightId = freight.getId();

        Booking booking = bookingRepository.saveAndFlush(Booking.builder()
                .cargoRequest(freight)
                .vehicle(vehicle)
                .driver(driver)
                .bookingStatus(Booking.BookingStatus.ACCEPTED)
                .commissionFee(BigDecimal.ZERO)
                .build());
        bookingId = booking.getId();

        Trip trip = tripRepository.saveAndFlush(Trip.builder()
                .booking(booking)
                .driver(driver)
                .vehicle(vehicle)
                .startLocation("Pickup")
                .destination("Destination")
                .tripStatus(Trip.TripStatus.ONGOING)
                .startTime(LocalDateTime.now().minusHours(1))
                .build());
        tripId = trip.getId();

        var transactions = new TransactionTemplate(transactionManager);
        CountDownLatch arrivalWritten = new CountDownLatch(1);
        CountDownLatch releaseArrival = new CountDownLatch(1);
        CountDownLatch completionStarted = new CountDownLatch(1);
        CountDownLatch completionFinished = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> arrival = executor.submit(() -> transactions.executeWithoutResult(status -> {
            Trip lockedTrip = tripRepository.findByIdForUpdate(tripId).orElseThrow();
            lockedTrip.setTripStatus(Trip.TripStatus.ARRIVED);
            lockedTrip.setArrivedAt(LocalDateTime.now());
            tripRepository.saveAndFlush(lockedTrip);
            arrivalWritten.countDown();
            await(releaseArrival);
        }));
        Future<Throwable> completion = executor.submit(() -> {
            await(arrivalWritten);
            completionStarted.countDown();
            try {
                tripLifecycleService.completeTrip(tripId, detachedDriver());
                return null;
            } catch (RuntimeException exception) {
                return exception;
            } finally {
                completionFinished.countDown();
            }
        });

        try {
            assertThat(arrivalWritten.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(completionStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(completionFinished.await(300, TimeUnit.MILLISECONDS)).isFalse();
        } finally {
            releaseArrival.countDown();
        }

        arrival.get(10, TimeUnit.SECONDS);
        assertThat(completion.get(10, TimeUnit.SECONDS)).isInstanceOf(RuntimeException.class);
        executor.shutdownNow();

        assertThat(tripRepository.findById(tripId).orElseThrow().getTripStatus())
                .isEqualTo(Trip.TripStatus.ARRIVED);
        assertThat(bookingRepository.findById(bookingId).orElseThrow().getBookingStatus())
                .isEqualTo(Booking.BookingStatus.ACCEPTED);
        assertThat(vehicleRepository.findById(vehicleId).orElseThrow().getAvailabilityStatus())
                .isEqualTo("on_trip");
    }

    @Test
    void concurrentShipperDeliveryConfirmationsCompleteOnlyOnce() throws Exception {
        User driver = saveUser(User.Role.DRIVER);
        User shipper = saveUser(User.Role.SHIPPER);
        Vehicle vehicle = vehicleRepository.saveAndFlush(Vehicle.builder()
                .userId(driver.getId())
                .truckType("Test truck")
                .plateNumber(unique("DELIVERY-CONFIRM"))
                .capacity(10.0)
                .availabilityStatus("on_trip")
                .build());
        vehicleId = vehicle.getId();

        Freight freight = freightRepository.saveAndFlush(Freight.builder()
                .shipper(shipper)
                .pickupLocation("Pickup")
                .destination("Destination")
                .materialType("General")
                .weightTons(BigDecimal.ONE)
                .budget(BigDecimal.valueOf(1000))
                .urgencyLevel("NORMAL")
                .status(Freight.FreightStatus.IN_TRANSIT)
                .matchedDriver(driver)
                .build());
        freightId = freight.getId();

        Booking booking = bookingRepository.saveAndFlush(Booking.builder()
                .cargoRequest(freight)
                .vehicle(vehicle)
                .driver(driver)
                .bookingStatus(Booking.BookingStatus.ACCEPTED)
                .commissionFee(BigDecimal.ZERO)
                .build());
        bookingId = booking.getId();

        Trip trip = tripRepository.saveAndFlush(Trip.builder()
                .booking(booking)
                .driver(driver)
                .vehicle(vehicle)
                .startLocation("Pickup")
                .destination("Destination")
                .tripType(Trip.TripType.MULTI_STOP)
                .totalStops(2)
                .completedStops(1)
                .tripStatus(Trip.TripStatus.ARRIVED)
                .startTime(LocalDateTime.now().minusHours(1))
                .arrivedAt(LocalDateTime.now())
                .build());
        tripId = trip.getId();
        tripStopRepository.saveAndFlush(TripStop.builder()
                .trip(trip)
                .stopOrder(1)
                .locationName("Completed intermediate stop")
                .stopStatus(TripStop.StopStatus.COMPLETED)
                .completedAt(LocalDateTime.now().minusMinutes(1))
                .build());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Trip> first = executor.submit(() -> {
                ready.countDown();
                await(start);
                return tripLifecycleService.confirmDeliveryByShipper(tripId, detachedShipper());
            });
            Future<Trip> second = executor.submit(() -> {
                ready.countDown();
                await(start);
                return tripLifecycleService.confirmDeliveryByShipper(tripId, detachedShipper());
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).getTripStatus())
                    .isEqualTo(Trip.TripStatus.COMPLETED);
            assertThat(second.get(10, TimeUnit.SECONDS).getTripStatus())
                    .isEqualTo(Trip.TripStatus.COMPLETED);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        Trip completed = tripRepository.findById(tripId).orElseThrow();
        assertThat(completed.getShipperDeliveryConfirmedBy().getId()).isEqualTo(shipperId);
        assertThat(completed.getShipperDeliveryConfirmedAt()).isNotNull();
        assertThat(completed.getCompletedStops()).isEqualTo(completed.getTotalStops());
        assertThat(bookingRepository.findById(bookingId).orElseThrow().getBookingStatus())
                .isEqualTo(Booking.BookingStatus.DELIVERED);
    }

    @Test
    void scheduledProcessingCannotCompleteMultiStopTripAwaitingShipperConfirmation() throws Exception {
        User driver = saveUser(User.Role.DRIVER);
        User shipper = saveUser(User.Role.SHIPPER);
        Vehicle vehicle = vehicleRepository.saveAndFlush(Vehicle.builder()
                .userId(driver.getId())
                .truckType("Test truck")
                .plateNumber(unique("SCHEDULED-TRIP"))
                .capacity(10.0)
                .availabilityStatus("on_trip")
                .build());
        vehicleId = vehicle.getId();

        Freight freight = freightRepository.saveAndFlush(Freight.builder()
                .shipper(shipper)
                .pickupLocation("Pickup")
                .destination("Destination")
                .materialType("General")
                .weightTons(BigDecimal.ONE)
                .budget(BigDecimal.valueOf(1000))
                .urgencyLevel("NORMAL")
                .status(Freight.FreightStatus.IN_TRANSIT)
                .matchedDriver(driver)
                .build());
        freightId = freight.getId();

        Booking booking = bookingRepository.saveAndFlush(Booking.builder()
                .cargoRequest(freight)
                .vehicle(vehicle)
                .driver(driver)
                .bookingStatus(Booking.BookingStatus.ACCEPTED)
                .commissionFee(BigDecimal.ZERO)
                .build());
        bookingId = booking.getId();

        Trip trip = tripRepository.saveAndFlush(Trip.builder()
                .booking(booking)
                .driver(driver)
                .vehicle(vehicle)
                .startLocation("Pickup")
                .destination("Destination")
                .tripType(Trip.TripType.MULTI_STOP)
                .totalStops(2)
                .completedStops(1)
                .tripStatus(Trip.TripStatus.ONGOING)
                .startTime(LocalDateTime.now().minusHours(1))
                .build());
        tripId = trip.getId();
        tripStopRepository.saveAndFlush(TripStop.builder()
                .trip(trip)
                .stopOrder(1)
                .locationName("Intermediate stop")
                .stopStatus(TripStop.StopStatus.COMPLETED)
                .build());

        var transactions = new TransactionTemplate(transactionManager);
        CountDownLatch arrivalWritten = new CountDownLatch(1);
        CountDownLatch releaseArrival = new CountDownLatch(1);
        CountDownLatch schedulerStarted = new CountDownLatch(1);
        CountDownLatch schedulerFinished = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> arrival = executor.submit(() -> transactions.executeWithoutResult(status -> {
            Trip lockedTrip = tripRepository.findByIdForUpdate(tripId).orElseThrow();
            lockedTrip.setTripStatus(Trip.TripStatus.ARRIVED);
            lockedTrip.setArrivedAt(LocalDateTime.now());
            tripRepository.saveAndFlush(lockedTrip);
            arrivalWritten.countDown();
            await(releaseArrival);
        }));
        Future<List<Trip>> scheduledCompletion = executor.submit(() -> {
            await(arrivalWritten);
            schedulerStarted.countDown();
            try {
                return tripStatusTransitionService.processAutomaticTransitions();
            } finally {
                schedulerFinished.countDown();
            }
        });

        try {
            assertThat(arrivalWritten.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(schedulerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(schedulerFinished.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseArrival.countDown();
        }

        arrival.get(10, TimeUnit.SECONDS);
        assertThat(scheduledCompletion.get(10, TimeUnit.SECONDS)).isEmpty();
        executor.shutdownNow();

        assertThat(tripRepository.findById(tripId).orElseThrow().getTripStatus())
                .isEqualTo(Trip.TripStatus.ARRIVED);
        assertThat(bookingRepository.findById(bookingId).orElseThrow().getBookingStatus())
                .isEqualTo(Booking.BookingStatus.ACCEPTED);
        assertThat(vehicleRepository.findById(vehicleId).orElseThrow().getAvailabilityStatus())
                .isEqualTo("on_trip");
    }

    @Test
    void concurrentFinalStopCompletionIsIdempotentAndKeepsTripActive() throws Exception {
        User driver = saveUser(User.Role.DRIVER);
        User shipper = saveUser(User.Role.SHIPPER);
        Vehicle vehicle = vehicleRepository.saveAndFlush(Vehicle.builder()
                .userId(driver.getId())
                .truckType("Test truck")
                .plateNumber(unique("STOP-RACE"))
                .capacity(10.0)
                .availabilityStatus("on_trip")
                .build());
        vehicleId = vehicle.getId();

        Freight freight = freightRepository.saveAndFlush(Freight.builder()
                .shipper(shipper)
                .pickupLocation("Pickup")
                .destination("Destination")
                .materialType("General")
                .weightTons(BigDecimal.ONE)
                .budget(BigDecimal.valueOf(1000))
                .urgencyLevel("NORMAL")
                .status(Freight.FreightStatus.IN_TRANSIT)
                .build());
        freightId = freight.getId();

        Booking booking = bookingRepository.saveAndFlush(Booking.builder()
                .cargoRequest(freight)
                .vehicle(vehicle)
                .driver(driver)
                .bookingStatus(Booking.BookingStatus.ACCEPTED)
                .commissionFee(BigDecimal.ZERO)
                .build());
        bookingId = booking.getId();

        Trip trip = tripRepository.saveAndFlush(Trip.builder()
                .booking(booking)
                .driver(driver)
                .vehicle(vehicle)
                .startLocation("Pickup")
                .destination("Destination")
                .tripType(Trip.TripType.MULTI_STOP)
                .totalStops(2)
                .completedStops(0)
                .tripStatus(Trip.TripStatus.ONGOING)
                .startTime(LocalDateTime.now().minusHours(1))
                .build());
        tripId = trip.getId();
        TripStop stop = tripStopRepository.saveAndFlush(TripStop.builder()
                .trip(trip)
                .stopOrder(1)
                .locationName("Intermediate stop")
                .stopStatus(TripStop.StopStatus.ARRIVED)
                .arrivedAt(LocalDateTime.now().minusMinutes(1))
                .build());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<TripStop> first = executor.submit(() -> {
                ready.countDown();
                await(start);
                return tripStopService.updateStopStatus(
                        stop.getId(), TripStop.StopStatus.COMPLETED, detachedDriver());
            });
            Future<TripStop> second = executor.submit(() -> {
                ready.countDown();
                await(start);
                return tripStopService.updateStopStatus(
                        stop.getId(), TripStop.StopStatus.COMPLETED, detachedDriver());
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            LocalDateTime firstCompletionTime = first.get(10, TimeUnit.SECONDS).getCompletedAt();
            LocalDateTime secondCompletionTime = second.get(10, TimeUnit.SECONDS).getCompletedAt();
            assertThat(Duration.between(firstCompletionTime, secondCompletionTime).abs())
                    .isLessThanOrEqualTo(Duration.ofNanos(2_000));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        Trip persistedTrip = tripRepository.findById(tripId).orElseThrow();
        assertThat(persistedTrip.getTripStatus()).isEqualTo(Trip.TripStatus.ONGOING);
        assertThat(persistedTrip.getCompletedStops()).isEqualTo(1);
        assertThat(tripStopRepository.findById(stop.getId()).orElseThrow().getStopStatus())
                .isEqualTo(TripStop.StopStatus.COMPLETED);
        LocalDateTime persistedCompletionTime = tripStopRepository.findById(stop.getId())
                .orElseThrow().getCompletedAt();
        Thread.sleep(5);
        assertThat(tripStopService.updateStopStatus(
                stop.getId(), TripStop.StopStatus.COMPLETED, detachedDriver()).getCompletedAt())
                .isEqualTo(persistedCompletionTime);
        assertThat(bookingRepository.findById(bookingId).orElseThrow().getBookingStatus())
                .isEqualTo(Booking.BookingStatus.ACCEPTED);
        assertThat(freightRepository.findById(freightId).orElseThrow().getStatus())
                .isEqualTo(Freight.FreightStatus.IN_TRANSIT);
    }

    private void appendInTransaction(
            TransactionTemplate transactions,
            User driver,
            RouteDataService.GpsLocationData gpsPoint,
            CountDownLatch snapshotsLoaded,
            CountDownLatch start,
            CountDownLatch firstWriteComplete,
            CountDownLatch releaseFirstTransaction,
            CountDownLatch secondWriteStarted,
            CountDownLatch secondTransactionComplete) {
        transactions.executeWithoutResult(status -> {
            Trip staleSnapshot = tripRepository.findById(tripId).orElseThrow();
            snapshotsLoaded.countDown();
            await(start);

            if (secondWriteStarted != null) {
                await(firstWriteComplete);
                secondWriteStarted.countDown();
            }

            routeDataService.addGpsLocation(staleSnapshot, gpsPoint, detachedDriver());

            if (releaseFirstTransaction != null) {
                firstWriteComplete.countDown();
                await(releaseFirstTransaction);
            }
            if (secondTransactionComplete != null) {
                secondTransactionComplete.countDown();
            }
        });
    }

    private User detachedDriver() {
        return User.builder().id(driverId).role(User.Role.DRIVER).build();
    }

    private User detachedShipper() {
        return User.builder().id(shipperId).role(User.Role.SHIPPER).build();
    }

    private User saveUser(User.Role role) {
        User user = userRepository.saveAndFlush(User.builder()
                .fullName(role + " GPS Test")
                .phone(unique("251"))
                .password("test-password")
                .role(role)
                .verificationStatus(true)
                .isActive(true)
                .build());
        if (role == User.Role.DRIVER) {
            driverId = user.getId();
        } else {
            shipperId = user.getId();
        }
        return user;
    }

    private RouteDataService.GpsLocationData gpsPoint(String latitude, String longitude, int second) {
        return new RouteDataService.GpsLocationData(
                new BigDecimal(latitude),
                new BigDecimal(longitude),
                null,
                new BigDecimal("5"),
                null,
                null,
                LocalDateTime.now().withNano(0).plusSeconds(second),
                Map.of());
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent GPS test coordination");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during concurrent GPS test", e);
        }
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @AfterEach
    void cleanUp() {
        if (tripId != null) tripRepository.deleteById(tripId);
        if (bookingId != null) bookingRepository.deleteById(bookingId);
        if (freightId != null) freightRepository.deleteById(freightId);
        if (vehicleId != null) vehicleRepository.deleteById(vehicleId);
        if (driverId != null) userRepository.deleteById(driverId);
        if (shipperId != null) userRepository.deleteById(shipperId);
    }
}
