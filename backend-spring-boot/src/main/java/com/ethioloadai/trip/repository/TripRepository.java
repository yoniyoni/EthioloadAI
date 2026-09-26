package com.ethioloadai.trip.repository;

import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.entity.Vehicle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {

    /** Find the trip for a specific booking (one-to-one). */
    Optional<Trip> findByBooking_Id(Long bookingId);

    /** Driver's trips via the booking relationship. */
    @Query("SELECT t FROM Trip t WHERE t.booking.driver.id = :driverId")
    List<Trip> findByDriverId(@Param("driverId") Long driverId);

    /** Driver's trips with pagination. */
    @Query("SELECT t FROM Trip t LEFT JOIN FETCH t.booking b LEFT JOIN FETCH b.driver LEFT JOIN FETCH b.vehicle LEFT JOIN FETCH b.cargoRequest c LEFT JOIN FETCH c.shipper LEFT JOIN FETCH t.tripStops WHERE t.booking.driver.id = :driverId ORDER BY t.startTime DESC")
    Page<Trip> findByDriverId(@Param("driverId") Long driverId, Pageable pageable);

    /** All ongoing trips - for admin monitoring. */
    List<Trip> findByTripStatus(Trip.TripStatus status);

    /** Find a trip with its stops pre-loaded (avoids N+1 when rendering stops). */
    @Query("SELECT t FROM Trip t LEFT JOIN FETCH t.tripStops WHERE t.id = :tripId")
    Optional<Trip> findByIdWithStops(@Param("tripId") Long tripId);

    /** Find trip and verify driver owns it (IDOR protection). */
    @Query("SELECT t FROM Trip t WHERE t.id = :tripId AND t.booking.driver.id = :driverId")
    Optional<Trip> findByIdAndDriverId(@Param("tripId") Long tripId,
                                       @Param("driverId") Long driverId);

    /** Find trip by booking with pessimistic lock for concurrency protection. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Trip t WHERE t.booking.id = :bookingId")
    Optional<Trip> findByBookingIdWithLock(@Param("bookingId") Long bookingId);

    /** Count trips by driver for analytics/limits (all-time, used for reporting). */
    @Query("SELECT COUNT(t) FROM Trip t WHERE t.booking.driver.id = :driverId")
    long countByDriverId(@Param("driverId") Long driverId);

    /** Count ONGOING trips by driver — used for concurrent-trip limit enforcement. */
    @Query(value = "SELECT COUNT(t.*) FROM trips t " +
                   "JOIN bookings b ON b.id = t.booking_id " +
                   "WHERE b.driver_id = :driverId AND t.trip_status = 'ongoing'",
           nativeQuery = true)
    long countOngoingByDriverId(@Param("driverId") Long driverId);

    /** Find all ongoing trips for a specific driver. */
    @Query("SELECT t FROM Trip t WHERE t.booking.driver.id = :driverId AND t.tripStatus = :status")
    List<Trip> findByDriverIdAndStatus(@Param("driverId") Long driverId, 
                                      @Param("status") Trip.TripStatus status);

        // -- Security-Enhanced Query Methods --------------------------------------

    /** Find trip by ID and shipper ID for ownership validation. */
    @Query("SELECT t FROM Trip t LEFT JOIN FETCH t.tripStops " +
           "WHERE t.id = :tripId AND t.booking.cargoRequest.shipper.id = :shipperId")
    Optional<Trip> findByIdAndShipperId(@Param("tripId") Long tripId, @Param("shipperId") Long shipperId);

    /** Find all trips with basic details (with booking for mapping). */
    @Query("SELECT t FROM Trip t LEFT JOIN FETCH t.booking b LEFT JOIN FETCH b.driver LEFT JOIN FETCH b.vehicle LEFT JOIN FETCH b.cargoRequest c LEFT JOIN FETCH c.shipper LEFT JOIN FETCH t.tripStops ORDER BY t.startTime DESC")
    Page<Trip> findAllWithBasicDetails(Pageable pageable);

    /** Find trips by shipper ID. */
    @Query("SELECT t FROM Trip t LEFT JOIN FETCH t.booking b LEFT JOIN FETCH b.driver LEFT JOIN FETCH b.vehicle LEFT JOIN FETCH b.cargoRequest c LEFT JOIN FETCH c.shipper LEFT JOIN FETCH t.tripStops WHERE t.booking.cargoRequest.shipper.id = :shipperId ORDER BY t.startTime DESC")
    Page<Trip> findByShipperId(@Param("shipperId") Long shipperId, Pageable pageable);

    /** Find trips by status with pagination. */
    @Query("SELECT t FROM Trip t LEFT JOIN FETCH t.booking b LEFT JOIN FETCH b.driver LEFT JOIN FETCH b.vehicle LEFT JOIN FETCH b.cargoRequest c LEFT JOIN FETCH c.shipper WHERE t.tripStatus = :tripStatus ORDER BY t.startTime DESC")
    Page<Trip> findByTripStatus(@Param("tripStatus") Trip.TripStatus tripStatus, Pageable pageable);

    /** Find trips by driver ID and status with pagination. */
    @Query("SELECT t FROM Trip t LEFT JOIN FETCH t.booking b LEFT JOIN FETCH b.driver LEFT JOIN FETCH b.vehicle LEFT JOIN FETCH b.cargoRequest c LEFT JOIN FETCH c.shipper WHERE t.booking.driver.id = :driverId AND t.tripStatus = :status ORDER BY t.startTime DESC")
    Page<Trip> findByDriverIdAndTripStatus(@Param("driverId") Long driverId, @Param("status") Trip.TripStatus status, Pageable pageable);

    /** Find trips by shipper ID and status. */
    @Query("SELECT t FROM Trip t LEFT JOIN FETCH t.booking b LEFT JOIN FETCH b.driver LEFT JOIN FETCH b.vehicle LEFT JOIN FETCH b.cargoRequest c LEFT JOIN FETCH c.shipper WHERE t.booking.cargoRequest.shipper.id = :shipperId AND t.tripStatus = :status ORDER BY t.startTime DESC")
    Page<Trip> findByShipperIdAndTripStatus(@Param("shipperId") Long shipperId, @Param("status") Trip.TripStatus status, Pageable pageable);

    /** Find trips by date range. */
    @Query("SELECT t FROM Trip t LEFT JOIN FETCH t.booking b LEFT JOIN FETCH b.driver LEFT JOIN FETCH b.vehicle LEFT JOIN FETCH b.cargoRequest c LEFT JOIN FETCH c.shipper WHERE t.startTime BETWEEN :startDate AND :endDate ORDER BY t.startTime DESC")
    Page<Trip> findByStartTimeBetween(@Param("startDate") java.time.LocalDateTime startDate, @Param("endDate") java.time.LocalDateTime endDate, Pageable pageable);

    /** Find trips by driver ID and date range. */
    @Query("SELECT t FROM Trip t LEFT JOIN FETCH t.booking b LEFT JOIN FETCH b.driver LEFT JOIN FETCH b.vehicle LEFT JOIN FETCH b.cargoRequest c LEFT JOIN FETCH c.shipper WHERE t.booking.driver.id = :driverId AND t.startTime BETWEEN :startDate AND :endDate ORDER BY t.startTime DESC")
    Page<Trip> findByDriverIdAndStartTimeBetween(@Param("driverId") Long driverId, 
                                                @Param("startDate") java.time.LocalDateTime startDate, 
                                                @Param("endDate") java.time.LocalDateTime endDate, 
                                                Pageable pageable);

    /** Find trips by shipper ID and date range. */
    @Query("SELECT t FROM Trip t LEFT JOIN FETCH t.booking b LEFT JOIN FETCH b.driver LEFT JOIN FETCH b.vehicle LEFT JOIN FETCH b.cargoRequest c LEFT JOIN FETCH c.shipper WHERE t.booking.cargoRequest.shipper.id = :shipperId AND t.startTime BETWEEN :startDate AND :endDate ORDER BY t.startTime DESC")
    Page<Trip> findByShipperIdAndStartTimeBetween(@Param("shipperId") Long shipperId, 
                                                @Param("startDate") java.time.LocalDateTime startDate, 
                                                @Param("endDate") java.time.LocalDateTime endDate, 
                                                Pageable pageable);

    /** Find trips requiring admin attention. */
    @Query("SELECT t FROM Trip t LEFT JOIN FETCH t.booking b LEFT JOIN FETCH b.driver LEFT JOIN FETCH b.vehicle LEFT JOIN FETCH b.cargoRequest c LEFT JOIN FETCH c.shipper WHERE t.tripStatus = 'ONGOING' AND t.startTime < :overdueThreshold ORDER BY t.startTime ASC")
    Page<Trip> findTripsRequiringAdminAttention(@Param("overdueThreshold") java.time.LocalDateTime overdueThreshold, Pageable pageable);

    /** Find driver trips requiring attention. */
    @Query("SELECT t FROM Trip t LEFT JOIN FETCH t.booking b LEFT JOIN FETCH b.driver LEFT JOIN FETCH b.vehicle LEFT JOIN FETCH b.cargoRequest c LEFT JOIN FETCH c.shipper WHERE t.booking.driver.id = :driverId AND t.tripStatus = 'ONGOING' AND t.startTime < :overdueThreshold ORDER BY t.startTime ASC")
    Page<Trip> findDriverTripsRequiringAttention(@Param("driverId") Long driverId, 
                                                @Param("overdueThreshold") java.time.LocalDateTime overdueThreshold, 
                                                Pageable pageable);

    /** Find shipper trips requiring attention. */
    @Query("SELECT t FROM Trip t LEFT JOIN FETCH t.booking b LEFT JOIN FETCH b.driver LEFT JOIN FETCH b.vehicle LEFT JOIN FETCH b.cargoRequest c LEFT JOIN FETCH c.shipper WHERE t.booking.cargoRequest.shipper.id = :shipperId AND t.tripStatus = 'ONGOING' AND t.startTime < :overdueThreshold ORDER BY t.startTime ASC")
    Page<Trip> findShipperTripsRequiringAttention(@Param("shipperId") Long shipperId, 
                                                 @Param("overdueThreshold") java.time.LocalDateTime overdueThreshold, 
                                                 Pageable pageable);

        // -- Vehicle Consistency Methods ------------------------------------------

    /** Find trip by ID with booking and vehicle information for consistency validation. */
    @Query("SELECT t FROM Trip t " +
           "LEFT JOIN FETCH t.booking b " +
           "LEFT JOIN FETCH b.vehicle v " +
           "WHERE t.id = :tripId")
    Optional<Trip> findByIdWithBookingAndVehicle(@Param("tripId") Long tripId);

    /** Find active trip for a specific vehicle. */
    @Query("SELECT t FROM Trip t " +
           "WHERE t.booking.vehicle.id = :vehicleId " +
           "AND t.tripStatus = 'ONGOING'")
    Optional<Trip> findActiveTripByVehicleId(@Param("vehicleId") Long vehicleId);

    /** Find trips by vehicle and status. */
    @Query("SELECT t FROM Trip t " +
           "WHERE t.booking.vehicle = :vehicle " +
           "AND t.tripStatus = :status")
    List<Trip> findByBooking_VehicleAndTripStatus(@Param("vehicle") Vehicle vehicle, 
                                                 @Param("status") Trip.TripStatus status);

    /** Find trips by driver, vehicle and status. */
    @Query("SELECT t FROM Trip t " +
           "WHERE t.booking.driver = :driver " +
           "AND t.booking.vehicle = :vehicle " +
           "AND t.tripStatus = :status")
    List<Trip> findByBooking_DriverAndBooking_VehicleAndTripStatus(@Param("driver") User driver,
                                                                  @Param("vehicle") Vehicle vehicle,
                                                                  @Param("status") Trip.TripStatus status);
}
