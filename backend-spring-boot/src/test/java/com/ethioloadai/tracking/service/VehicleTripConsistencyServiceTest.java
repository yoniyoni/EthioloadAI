package com.ethioloadai.tracking.service;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.tracking.dto.LocationUpdateRequest;
import com.ethioloadai.tracking.exception.TrackingAuthorizationException;
import com.ethioloadai.tracking.exception.VehicleConsistencyException;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.exception.TripNotFoundException;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Vehicle Trip Consistency Service Tests")
class VehicleTripConsistencyServiceTest {

    @Mock
    private TripRepository tripRepository;
    
    @Mock
    private TrackingAuthorizationService authorizationService;

    @Mock
    private VehicleRepository vehicleRepository;
    
    @InjectMocks
    private VehicleTripConsistencyServiceImpl vehicleTripConsistencyService;
    
    private Trip testTrip;
    private Vehicle testVehicle;
    private Vehicle differentVehicle;
    private User testUser;
    private Booking testBooking;
    private LocationUpdateRequest validLocationRequest;

    @BeforeEach
    void setUp() {
        // Create test entities
        testUser = User.builder()
            .id(1L)
            .fullName("Test Driver")
            .phone("251911123456")
            .role(User.Role.DRIVER)
            .build();
        
        testVehicle = Vehicle.builder()
            .id(100L)
            .userId(1L)
            .plateNumber("AA-123456")
            .build();
            
        differentVehicle = Vehicle.builder()
            .id(200L)
            .plateNumber("BB-654321")
            .build();
        
        Freight freight = Freight.builder()
            .id(1L)
            .pickupLocation("Addis Ababa")
            .destination("Dire Dawa")
            .build();
        
        testBooking = Booking.builder()
            .id(1L)
            .vehicle(testVehicle)
            .driver(testUser)
            .cargoRequest(freight)
            .build();
        
        testTrip = Trip.builder()
            .id(1L)
            .booking(testBooking)
            .tripStatus(Trip.TripStatus.ONGOING)
            .startLocation("Addis Ababa")
            .destination("Dire Dawa")
            .build();
        
        validLocationRequest = LocationUpdateRequest.builder()
            .vehicleId(testVehicle.getId())
            .latitude(new BigDecimal("9.03"))
            .longitude(new BigDecimal("38.74"))
            .timestamp(LocalDateTime.now())
            .speed(new BigDecimal("60.0"))
            .accuracy(new BigDecimal("5.0"))
            .build();
    }

    @Nested
    @DisplayName("Vehicle Trip Consistency Validation")
    class VehicleTripConsistencyValidation {

        @Test
        @DisplayName("Should validate successfully when vehicle matches trip")
        void shouldValidateSuccessfullyWhenVehicleMatches() {
            // Arrange
            when(authorizationService.canSubmitLocationUpdates(testUser))
                .thenReturn(true);
            when(tripRepository.findByIdWithBookingAndVehicle(1L))
                .thenReturn(Optional.of(testTrip));

            // Act
            VehicleTripConsistencyService.VehicleConsistencyValidation result = 
                vehicleTripConsistencyService.validateVehicleTripConsistency(1L, testVehicle.getId(), testUser);

            // Assert
            assertTrue(result.isValid());
            assertEquals(testVehicle.getId(), result.getExpectedVehicleId());
            assertEquals(testVehicle.getId(), result.getActualVehicleId());
            assertEquals(1L, result.getTripId());
            
            verify(authorizationService).canSubmitLocationUpdates(testUser);
            verify(tripRepository).findByIdWithBookingAndVehicle(1L);
        }

        @Test
        @DisplayName("Should fail validation when vehicle does not match trip")
        void shouldFailValidationWhenVehicleDoesNotMatch() {
            // Arrange
            when(authorizationService.canSubmitLocationUpdates(testUser))
                .thenReturn(true);
            when(tripRepository.findByIdWithBookingAndVehicle(1L))
                .thenReturn(Optional.of(testTrip));

            // Act
            VehicleTripConsistencyService.VehicleConsistencyValidation result = 
                vehicleTripConsistencyService.validateVehicleTripConsistency(1L, differentVehicle.getId(), testUser);

            // Assert
            assertFalse(result.isValid());
            assertEquals(testVehicle.getId(), result.getExpectedVehicleId());
            assertEquals(differentVehicle.getId(), result.getActualVehicleId());
            assertEquals(1L, result.getTripId());
            assertTrue(result.getReason().contains("Vehicle mismatch"));
        }

        @Test
        @DisplayName("Should fail validation when trip is not in trackable state")
        void shouldFailValidationWhenTripNotTrackable() {
            // Arrange
            testTrip.setTripStatus(Trip.TripStatus.COMPLETED);
            when(authorizationService.canSubmitLocationUpdates(testUser))
                .thenReturn(true);
            when(tripRepository.findByIdWithBookingAndVehicle(1L))
                .thenReturn(Optional.of(testTrip));

            // Act
            VehicleTripConsistencyService.VehicleConsistencyValidation result = 
                vehicleTripConsistencyService.validateVehicleTripConsistency(1L, testVehicle.getId(), testUser);

            // Assert
            assertFalse(result.isValid());
            assertTrue(result.getReason().contains("not trackable"));
        }

        @Test
        @DisplayName("Should throw TripNotFoundException when trip does not exist")
        void shouldThrowTripNotFoundExceptionWhenTripDoesNotExist() {
            // Arrange
            when(authorizationService.canSubmitLocationUpdates(testUser))
                .thenReturn(true);
            when(tripRepository.findByIdWithBookingAndVehicle(999L))
                .thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(TripNotFoundException.class, () ->
                vehicleTripConsistencyService.validateVehicleTripConsistency(999L, testVehicle.getId(), testUser)
            );
        }

        @Test
        @DisplayName("Should throw authorization exception when user not authorized")
        void shouldThrowAuthorizationExceptionWhenUserNotAuthorized() {
            // Arrange
            when(authorizationService.canSubmitLocationUpdates(testUser))
                .thenReturn(false);

            // Act & Assert
            assertThrows(TrackingAuthorizationException.class, () ->
                vehicleTripConsistencyService.validateVehicleTripConsistency(1L, testVehicle.getId(), testUser)
            );
            
            verify(tripRepository, never()).findByIdWithBookingAndVehicle(anyLong());
        }
    }

    @Nested
    @DisplayName("Location Update Validation")
    class LocationUpdateValidation {

        @Test
        @DisplayName("Should validate location update successfully")
        void shouldValidateLocationUpdateSuccessfully() {
            // Arrange
            when(authorizationService.canSubmitLocationUpdates(testUser))
                .thenReturn(true);
            when(tripRepository.findByIdWithBookingAndVehicle(1L))
                .thenReturn(Optional.of(testTrip));

            // Act
            VehicleTripConsistencyService.VehicleConsistencyValidation result = 
                vehicleTripConsistencyService.validateLocationUpdate(1L, validLocationRequest, testUser);

            // Assert
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("Should throw exception when vehicle ID is missing")
        void shouldThrowExceptionWhenVehicleIdMissing() {
            // Arrange
            LocationUpdateRequest requestWithoutVehicle = LocationUpdateRequest.builder()
                .latitude(new BigDecimal("9.03"))
                .longitude(new BigDecimal("38.74"))
                .timestamp(LocalDateTime.now())
                .build();

            // Act & Assert
            VehicleConsistencyException exception = assertThrows(VehicleConsistencyException.class, () ->
                vehicleTripConsistencyService.validateLocationUpdate(1L, requestWithoutVehicle, testUser)
            );
            
            assertTrue(exception.getMessage().contains("missing required vehicleId"));
        }
    }

    @Nested
    @DisplayName("Vehicle Authorization Queries")
    class VehicleAuthorizationQueries {

        @Test
        @DisplayName("Should return authorized vehicle ID for trip")
        void shouldReturnAuthorizedVehicleIdForTrip() {
            // Arrange
            when(tripRepository.findByIdWithBookingAndVehicle(1L))
                .thenReturn(Optional.of(testTrip));
            when(authorizationService.hasVehicleAccess(testVehicle, testUser))
                .thenReturn(true);
            when(authorizationService.isTripVehicleConsistent(testTrip, testVehicle, testUser))
                .thenReturn(true);

            // Act
            Long vehicleId = vehicleTripConsistencyService.getAuthorizedVehicleForTrip(1L, testUser);

            // Assert
            assertEquals(testVehicle.getId(), vehicleId);
        }

        @Test
        @DisplayName("Should deny a driver access to another fleet's trip vehicle")
        void shouldDenyDriverAccessToAnotherFleetsTripVehicle() {
            User otherDriver = User.builder()
                .id(2L)
                .role(User.Role.DRIVER)
                .fleetOwnerId(20L)
                .build();
            Vehicle otherVehicle = Vehicle.builder()
                .id(200L)
                .userId(2L)
                .fleetOwnerId(20L)
                .build();
            Booking otherBooking = Booking.builder()
                .vehicle(otherVehicle)
                .driver(otherDriver)
                .build();
            Trip otherTrip = Trip.builder().id(2L).booking(otherBooking).build();
            when(tripRepository.findByIdWithBookingAndVehicle(2L)).thenReturn(Optional.of(otherTrip));
            when(authorizationService.hasVehicleAccess(otherVehicle, testUser)).thenReturn(false);

            assertThrows(TripNotFoundException.class,
                () -> vehicleTripConsistencyService.getAuthorizedVehicleForTrip(2L, testUser));
            verify(authorizationService, never()).isTripVehicleConsistent(otherTrip, otherVehicle, testUser);
        }

        @Test
        @DisplayName("Should preserve admin access to any trip vehicle")
        void shouldPreserveAdminAccessToAnyTripVehicle() {
            User admin = User.builder().id(99L).role(User.Role.ADMIN).build();
            when(tripRepository.findByIdWithBookingAndVehicle(1L)).thenReturn(Optional.of(testTrip));
            when(authorizationService.hasVehicleAccess(testVehicle, admin)).thenReturn(true);
            when(authorizationService.isTripVehicleConsistent(testTrip, testVehicle, admin)).thenReturn(true);

            assertEquals(testVehicle.getId(),
                vehicleTripConsistencyService.getAuthorizedVehicleForTrip(1L, admin));
        }

        @Test
        @DisplayName("Should allow a fleet owner to access a trip assigned within their fleet")
        void shouldAllowFleetOwnerAccessToOwnFleetTripVehicle() {
            User fleetOwner = User.builder().id(10L).role(User.Role.FLEET_OWNER).build();
            User fleetDriver = User.builder().id(4L).role(User.Role.DRIVER).fleetOwnerId(10L).build();
            Vehicle fleetVehicle = Vehicle.builder().id(101L).userId(4L).fleetOwnerId(10L).build();
            Booking fleetBooking = Booking.builder().vehicle(fleetVehicle).driver(fleetDriver).build();
            Trip fleetTrip = Trip.builder().id(6L).booking(fleetBooking).build();
            when(tripRepository.findByIdWithBookingAndVehicle(6L)).thenReturn(Optional.of(fleetTrip));
            when(authorizationService.hasVehicleAccess(fleetVehicle, fleetOwner)).thenReturn(true);
            when(authorizationService.isTripVehicleConsistent(fleetTrip, fleetVehicle, fleetOwner))
                .thenReturn(true);

            assertEquals(fleetVehicle.getId(),
                vehicleTripConsistencyService.getAuthorizedVehicleForTrip(6L, fleetOwner));
        }

        @Test
        @DisplayName("Should deny a fleet owner access to another fleet's trip vehicle")
        void shouldDenyFleetOwnerAccessToAnotherFleetsTripVehicle() {
            User fleetOwner = User.builder().id(10L).role(User.Role.FLEET_OWNER).build();
            User otherDriver = User.builder().id(2L).role(User.Role.DRIVER).fleetOwnerId(20L).build();
            Vehicle otherVehicle = Vehicle.builder().id(200L).userId(2L).fleetOwnerId(20L).build();
            Booking otherBooking = Booking.builder().vehicle(otherVehicle).driver(otherDriver).build();
            Trip otherTrip = Trip.builder().id(2L).booking(otherBooking).build();
            when(tripRepository.findByIdWithBookingAndVehicle(2L)).thenReturn(Optional.of(otherTrip));
            when(authorizationService.hasVehicleAccess(otherVehicle, fleetOwner)).thenReturn(false);

            assertThrows(TripNotFoundException.class,
                () -> vehicleTripConsistencyService.getAuthorizedVehicleForTrip(2L, fleetOwner));
        }

        @Test
        @DisplayName("Should allow a shipper to access a vehicle for their own trip")
        void shouldAllowShipperAccessToOwnTripsVehicle() {
            User shipper = User.builder().id(3L).role(User.Role.SHIPPER).build();
            when(tripRepository.findByIdAndShipperId(1L, shipper.getId())).thenReturn(Optional.of(testTrip));

            assertEquals(testVehicle.getId(),
                vehicleTripConsistencyService.getAuthorizedVehicleForTrip(1L, shipper));
            verify(tripRepository, never()).findByIdWithBookingAndVehicle(1L);
        }

        @Test
        @DisplayName("Should return not found for unknown or unauthorized trips")
        void shouldReturnNotFoundForUnknownTrip() {
            when(tripRepository.findByIdWithBookingAndVehicle(44L)).thenReturn(Optional.empty());

            assertThrows(TripNotFoundException.class,
                () -> vehicleTripConsistencyService.getAuthorizedVehicleForTrip(44L, testUser));
        }

        @Test
        @DisplayName("Should return active trip ID for vehicle")
        void shouldReturnActiveTripIdForVehicle() {
            // Arrange
            when(vehicleRepository.findById(100L)).thenReturn(Optional.of(testVehicle));
            when(authorizationService.hasVehicleAccess(testVehicle, testUser)).thenReturn(true);
            when(tripRepository.findActiveTripByVehicleId(testVehicle.getId()))
                .thenReturn(Optional.of(testTrip));
            when(authorizationService.isTripVehicleConsistent(testTrip, testVehicle, testUser))
                .thenReturn(true);

            // Act
            Long activeTripId = vehicleTripConsistencyService.getActiveTripForVehicle(testVehicle.getId(), testUser);

            // Assert
            assertEquals(testTrip.getId(), activeTripId);
        }

        @Test
        @DisplayName("Should deny access to another fleet's vehicle active trip")
        void shouldDenyAccessToAnotherFleetsVehicleActiveTrip() {
            Vehicle otherVehicle = Vehicle.builder().id(200L).userId(2L).fleetOwnerId(20L).build();
            when(vehicleRepository.findById(200L)).thenReturn(Optional.of(otherVehicle));
            when(authorizationService.hasVehicleAccess(otherVehicle, testUser)).thenReturn(false);

            assertThrows(TrackingAuthorizationException.class,
                () -> vehicleTripConsistencyService.getActiveTripForVehicle(otherVehicle.getId(), testUser));
            verify(tripRepository, never()).findActiveTripByVehicleId(otherVehicle.getId());
        }

        @Test
        @DisplayName("Should deny a fleet driver access to another driver's active trip")
        void shouldDenyFleetDriverAccessToAnotherDriversActiveTrip() {
            User fleetDriver = User.builder().id(4L).role(User.Role.DRIVER).fleetOwnerId(10L).build();
            Vehicle fleetVehicle = Vehicle.builder().id(101L).userId(2L).fleetOwnerId(10L).build();
            Trip anotherDriversTrip = Trip.builder().id(5L).booking(testBooking).build();
            when(vehicleRepository.findById(101L)).thenReturn(Optional.of(fleetVehicle));
            when(authorizationService.hasVehicleAccess(fleetVehicle, fleetDriver)).thenReturn(true);
            when(tripRepository.findActiveTripByVehicleId(fleetVehicle.getId()))
                .thenReturn(Optional.of(anotherDriversTrip));
            when(authorizationService.isTripVehicleConsistent(anotherDriversTrip, fleetVehicle, fleetDriver))
                .thenReturn(false);

            assertThrows(TrackingAuthorizationException.class,
                () -> vehicleTripConsistencyService.getActiveTripForVehicle(fleetVehicle.getId(), fleetDriver));
        }

        @Test
        @DisplayName("Should preserve admin access to an active trip")
        void shouldPreserveAdminAccessToActiveTrip() {
            User admin = User.builder().id(99L).role(User.Role.ADMIN).build();
            when(vehicleRepository.findById(100L)).thenReturn(Optional.of(testVehicle));
            when(authorizationService.hasVehicleAccess(testVehicle, admin)).thenReturn(true);
            when(tripRepository.findActiveTripByVehicleId(testVehicle.getId())).thenReturn(Optional.of(testTrip));
            when(authorizationService.isTripVehicleConsistent(testTrip, testVehicle, admin)).thenReturn(true);

            assertEquals(testTrip.getId(),
                vehicleTripConsistencyService.getActiveTripForVehicle(testVehicle.getId(), admin));
        }

        @Test
        @DisplayName("Should return null when vehicle has no active trip")
        void shouldReturnNullWhenVehicleHasNoActiveTrip() {
            // Arrange
            when(vehicleRepository.findById(100L)).thenReturn(Optional.of(testVehicle));
            when(authorizationService.hasVehicleAccess(testVehicle, testUser)).thenReturn(true);
            when(tripRepository.findActiveTripByVehicleId(testVehicle.getId()))
                .thenReturn(Optional.empty());

            // Act
            Long activeTripId = vehicleTripConsistencyService.getActiveTripForVehicle(testVehicle.getId(), testUser);

            // Assert
            assertNull(activeTripId);
        }

        @Test
        @DisplayName("Should preserve null result for an unknown vehicle")
        void shouldReturnNullForUnknownVehicle() {
            when(vehicleRepository.findById(404L)).thenReturn(Optional.empty());

            assertNull(vehicleTripConsistencyService.getActiveTripForVehicle(404L, testUser));
            verify(tripRepository, never()).findActiveTripByVehicleId(404L);
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesAndErrorHandling {

        @Test
        @DisplayName("Should handle null vehicle ID gracefully")
        void shouldHandleNullVehicleIdGracefully() {
            // Arrange
            when(authorizationService.canSubmitLocationUpdates(testUser))
                .thenReturn(true);
            when(tripRepository.findByIdWithBookingAndVehicle(1L))
                .thenReturn(Optional.of(testTrip));

            // Act
            VehicleTripConsistencyService.VehicleConsistencyValidation result = 
                vehicleTripConsistencyService.validateVehicleTripConsistency(1L, null, testUser);

            // Assert
            assertFalse(result.isValid());
            assertTrue(result.getReason().contains("Vehicle mismatch"));
        }

        @Test
        @DisplayName("Should handle repository exceptions gracefully")
        void shouldHandleRepositoryExceptionsGracefully() {
            // Arrange
            when(authorizationService.canSubmitLocationUpdates(testUser))
                .thenReturn(true);
            when(tripRepository.findByIdWithBookingAndVehicle(1L))
                .thenThrow(new RuntimeException("Database error"));

            // Act & Assert
            assertThrows(RuntimeException.class, () ->
                vehicleTripConsistencyService.validateVehicleTripConsistency(1L, testVehicle.getId(), testUser)
            );
        }
    }
}