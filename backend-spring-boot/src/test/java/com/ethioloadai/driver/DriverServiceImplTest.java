package com.ethioloadai.driver;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.driver.dto.DriverFilterRequest;
import com.ethioloadai.driver.dto.DriverResponse;
import com.ethioloadai.driver.service.DriverServiceImpl;
import com.ethioloadai.rating.repository.RatingRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DriverServiceImpl}.
 *
 * <h2>Coverage</h2>
 * <ul>
 *   <li>Status filter routing — correct repository method chosen per status</li>
 *   <li>deriveStatus — all 4 combinations of verificationStatus × isActive</li>
 *   <li>Computed fields — rating, totalDeliveries, successRate, isAvailable</li>
 *   <li>Schema gaps — yearsExperience always 0</li>
 *   <li>DTO safety — password not exposed; vehicle omits fleetOwnerId, lat/lng</li>
 *   <li>Pagination — parameters forwarded to repository</li>
 *   <li>Empty results handled gracefully</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DriverServiceImpl")
class DriverServiceImplTest {

    @Mock private UserRepository    userRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private RatingRepository  ratingRepository;

    @InjectMocks
    private DriverServiceImpl driverService;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private User driver(Long id, String name, boolean verified, boolean active) {
        User u = new User();
        u.setId(id);
        u.setFullName(name);
        u.setPhone("+25191000000" + id);
        u.setEmail("driver" + id + "@test.com");
        u.setRole(User.Role.DRIVER);
        u.setVerificationStatus(verified);
        u.setIsActive(active);
        u.setLocation("Addis Ababa");
        return u;
    }

    private Vehicle availableVehicle(Long id, Long driverId) {
        Vehicle v = new Vehicle();
        v.setId(id);
        v.setUserId(driverId);
        v.setTruckType("flatbed");
        v.setPlateNumber("AA" + id + "-001");
        v.setCapacity(10.0);
        v.setCurrentCity("Addis Ababa");
        v.setAvailabilityStatus("available");
        v.setFleetOwnerId(null);
        return v;
    }

    private Vehicle unavailableVehicle(Long id, Long driverId) {
        Vehicle v = availableVehicle(id, driverId);
        v.setAvailabilityStatus("unavailable");
        return v;
    }

    /**
     * Builds a typed {@code List<Object[]>} row list.
     * Required because {@code List.of(new Object[]{...})} resolves to
     * {@code List<Object>} rather than {@code List<Object[]>} due to Java varargs
     * and array-to-Object coercion.
     */
    private static List<Object[]> rows(Object[]... arrays) {
        List<Object[]> list = new ArrayList<>();
        for (Object[] arr : arrays) {
            list.add(arr);
        }
        return list;
    }

    private static List<Object[]> emptyRows() {
        return new ArrayList<>();
    }

    /** Stub all four bulk repo calls to return empty (driver with no history). */
    private void stubEmptyStats(Set<Long> ids) {
        List<Object[]> empty = emptyRows();
        when(vehicleRepository.findByUserId(anyLong())).thenReturn(List.of());
        when(bookingRepository.countByDriverIdInAndStatus(ids, Booking.BookingStatus.COMPLETED))
                .thenReturn(empty);
        when(bookingRepository.countByDriverIdIn(ids)).thenReturn(empty);
        when(ratingRepository.findAverageRatingsForDriverIds(ids)).thenReturn(empty);
    }

    // ── deriveStatus ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deriveStatus")
    class DeriveStatus {

        @Test
        @DisplayName("verified=true, active=true → active")
        void verifiedAndActive() {
            assertThat(DriverServiceImpl.deriveStatus(driver(1L, "A", true, true)))
                    .isEqualTo("active");
        }

        @Test
        @DisplayName("verified=true, active=false → approved")
        void verifiedNotActive() {
            assertThat(DriverServiceImpl.deriveStatus(driver(1L, "A", true, false)))
                    .isEqualTo("approved");
        }

        @Test
        @DisplayName("verified=false, active=true → under_review")
        void notVerifiedButActive() {
            assertThat(DriverServiceImpl.deriveStatus(driver(1L, "A", false, true)))
                    .isEqualTo("under_review");
        }

        @Test
        @DisplayName("verified=false, active=false → suspended")
        void notVerifiedNotActive() {
            assertThat(DriverServiceImpl.deriveStatus(driver(1L, "A", false, false)))
                    .isEqualTo("suspended");
        }
    }

    // ── Status filter routing ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Status filter routing")
    class StatusFilterRouting {

        private final Pageable pageable = PageRequest.of(0, 20);

        @Test
        @DisplayName("null status → findByRole")
        void nullStatusUsesUnfilteredQuery() {
            DriverFilterRequest filter = DriverFilterRequest.builder().build();
            when(userRepository.findByRole(User.Role.DRIVER, pageable)).thenReturn(Page.empty(pageable));

            driverService.listDrivers(filter, pageable);

            verify(userRepository).findByRole(User.Role.DRIVER, pageable);
            verify(userRepository, never()).findByRoleAndVerificationStatusAndIsActive(any(), any(), any(), any());
            verify(userRepository, never()).findByRoleAndVerificationStatus(any(), any(), any());
            verify(userRepository, never()).findByRoleAndIsActive(any(), any(), any());
        }

        @Test
        @DisplayName("status=active → findByRoleAndVerificationStatusAndIsActive(true, true)")
        void activeStatusUsesVerifiedAndActive() {
            DriverFilterRequest filter = DriverFilterRequest.builder().status("active").build();
            when(userRepository.findByRoleAndVerificationStatusAndIsActive(
                    User.Role.DRIVER, true, true, pageable)).thenReturn(Page.empty(pageable));

            driverService.listDrivers(filter, pageable);

            verify(userRepository).findByRoleAndVerificationStatusAndIsActive(
                    User.Role.DRIVER, true, true, pageable);
        }

        @Test
        @DisplayName("status=approved → findByRoleAndVerificationStatus(true)")
        void approvedStatusUsesVerifiedOnly() {
            DriverFilterRequest filter = DriverFilterRequest.builder().status("approved").build();
            when(userRepository.findByRoleAndVerificationStatus(
                    User.Role.DRIVER, true, pageable)).thenReturn(Page.empty(pageable));

            driverService.listDrivers(filter, pageable);

            verify(userRepository).findByRoleAndVerificationStatus(User.Role.DRIVER, true, pageable);
        }

        @Test
        @DisplayName("status=under_review → findByRoleAndVerificationStatusAndIsActive(false, true)")
        void underReviewStatus() {
            DriverFilterRequest filter = DriverFilterRequest.builder().status("under_review").build();
            when(userRepository.findByRoleAndVerificationStatusAndIsActive(
                    User.Role.DRIVER, false, true, pageable)).thenReturn(Page.empty(pageable));

            driverService.listDrivers(filter, pageable);

            verify(userRepository).findByRoleAndVerificationStatusAndIsActive(
                    User.Role.DRIVER, false, true, pageable);
        }

        @Test
        @DisplayName("status=submitted → same query as under_review")
        void submittedAliasForUnderReview() {
            DriverFilterRequest filter = DriverFilterRequest.builder().status("submitted").build();
            when(userRepository.findByRoleAndVerificationStatusAndIsActive(
                    User.Role.DRIVER, false, true, pageable)).thenReturn(Page.empty(pageable));

            driverService.listDrivers(filter, pageable);

            verify(userRepository).findByRoleAndVerificationStatusAndIsActive(
                    User.Role.DRIVER, false, true, pageable);
        }

        @Test
        @DisplayName("status=suspended → findByRoleAndIsActive(false)")
        void suspendedStatus() {
            DriverFilterRequest filter = DriverFilterRequest.builder().status("suspended").build();
            when(userRepository.findByRoleAndIsActive(
                    User.Role.DRIVER, false, pageable)).thenReturn(Page.empty(pageable));

            driverService.listDrivers(filter, pageable);

            verify(userRepository).findByRoleAndIsActive(User.Role.DRIVER, false, pageable);
        }

        @Test
        @DisplayName("unknown status → falls back to findByRole")
        void unknownStatusFallsBackToAll() {
            DriverFilterRequest filter = DriverFilterRequest.builder().status("bogus").build();
            when(userRepository.findByRole(User.Role.DRIVER, pageable)).thenReturn(Page.empty(pageable));

            driverService.listDrivers(filter, pageable);

            verify(userRepository).findByRole(User.Role.DRIVER, pageable);
        }
    }

    // ── Computed fields ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Computed fields")
    class ComputedFields {

        private final Pageable pageable = PageRequest.of(0, 20);

        @Test
        @DisplayName("rating populated from bulk rating query")
        void ratingFromBulkQuery() {
            User d = driver(1L, "Abebe", true, true);
            Page<User> page = new PageImpl<>(List.of(d), pageable, 1);
            Set<Long> ids = Set.of(1L);

            List<Object[]> ratingRows = rows(new Object[]{1L, 4.5});
            List<Object[]> empty      = emptyRows();

            when(userRepository.findByRole(User.Role.DRIVER, pageable)).thenReturn(page);
            when(vehicleRepository.findByUserId(1L)).thenReturn(List.of());
            when(bookingRepository.countByDriverIdInAndStatus(ids, Booking.BookingStatus.COMPLETED))
                    .thenReturn(empty);
            when(bookingRepository.countByDriverIdIn(ids)).thenReturn(empty);
            when(ratingRepository.findAverageRatingsForDriverIds(ids)).thenReturn(ratingRows);

            Page<DriverResponse> result = driverService.listDrivers(
                    DriverFilterRequest.builder().build(), pageable);

            assertThat(result.getContent().get(0).getRating()).isEqualTo(4.5);
        }

        @Test
        @DisplayName("totalDeliveries from completed booking count")
        void totalDeliveriesFromCompletedBookings() {
            User d = driver(1L, "Abebe", true, true);
            Page<User> page = new PageImpl<>(List.of(d), pageable, 1);
            Set<Long> ids = Set.of(1L);

            List<Object[]> completedRows = rows(new Object[]{1L, 7L});
            List<Object[]> totalRows     = rows(new Object[]{1L, 10L});
            List<Object[]> emptyRatings  = emptyRows();

            when(userRepository.findByRole(User.Role.DRIVER, pageable)).thenReturn(page);
            when(vehicleRepository.findByUserId(1L)).thenReturn(List.of());
            when(bookingRepository.countByDriverIdInAndStatus(ids, Booking.BookingStatus.COMPLETED))
                    .thenReturn(completedRows);
            when(bookingRepository.countByDriverIdIn(ids)).thenReturn(totalRows);
            when(ratingRepository.findAverageRatingsForDriverIds(ids)).thenReturn(emptyRatings);

            Page<DriverResponse> result = driverService.listDrivers(
                    DriverFilterRequest.builder().build(), pageable);

            assertThat(result.getContent().get(0).getTotalDeliveries()).isEqualTo(7);
        }

        @Test
        @DisplayName("successRate = completed / total * 100")
        void successRateComputed() {
            User d = driver(1L, "Abebe", true, true);
            Page<User> page = new PageImpl<>(List.of(d), pageable, 1);
            Set<Long> ids = Set.of(1L);

            List<Object[]> completedRows = rows(new Object[]{1L, 8L});
            List<Object[]> totalRows     = rows(new Object[]{1L, 10L});
            List<Object[]> emptyRatings  = emptyRows();

            when(userRepository.findByRole(User.Role.DRIVER, pageable)).thenReturn(page);
            when(vehicleRepository.findByUserId(1L)).thenReturn(List.of());
            when(bookingRepository.countByDriverIdInAndStatus(ids, Booking.BookingStatus.COMPLETED))
                    .thenReturn(completedRows);
            when(bookingRepository.countByDriverIdIn(ids)).thenReturn(totalRows);
            when(ratingRepository.findAverageRatingsForDriverIds(ids)).thenReturn(emptyRatings);

            Page<DriverResponse> result = driverService.listDrivers(
                    DriverFilterRequest.builder().build(), pageable);

            // 8 / 10 * 100 = 80.0
            assertThat(result.getContent().get(0).getSuccessRate()).isEqualTo(80.0);
        }

        @Test
        @DisplayName("successRate is null when driver has no bookings")
        void successRateNullWhenNoBookings() {
            User d = driver(1L, "Abebe", true, true);
            Page<User> page = new PageImpl<>(List.of(d), pageable, 1);
            Set<Long> ids = Set.of(1L);

            when(userRepository.findByRole(User.Role.DRIVER, pageable)).thenReturn(page);
            stubEmptyStats(ids);

            Page<DriverResponse> result = driverService.listDrivers(
                    DriverFilterRequest.builder().build(), pageable);

            assertThat(result.getContent().get(0).getSuccessRate()).isNull();
        }

        @Test
        @DisplayName("isAvailable=true when any vehicle is available")
        void isAvailableTrueWhenAnyVehicleAvailable() {
            User d = driver(1L, "Abebe", true, true);
            Page<User> page = new PageImpl<>(List.of(d), pageable, 1);
            Set<Long> ids = Set.of(1L);
            List<Object[]> empty = emptyRows();

            when(userRepository.findByRole(User.Role.DRIVER, pageable)).thenReturn(page);
            when(vehicleRepository.findByUserId(1L))
                    .thenReturn(List.of(unavailableVehicle(10L, 1L), availableVehicle(11L, 1L)));
            when(bookingRepository.countByDriverIdInAndStatus(ids, Booking.BookingStatus.COMPLETED))
                    .thenReturn(empty);
            when(bookingRepository.countByDriverIdIn(ids)).thenReturn(empty);
            when(ratingRepository.findAverageRatingsForDriverIds(ids)).thenReturn(empty);

            Page<DriverResponse> result = driverService.listDrivers(
                    DriverFilterRequest.builder().build(), pageable);

            assertThat(result.getContent().get(0).getIsAvailable()).isTrue();
        }

        @Test
        @DisplayName("isAvailable=false when all vehicles are unavailable")
        void isAvailableFalseWhenAllUnavailable() {
            User d = driver(1L, "Abebe", true, true);
            Page<User> page = new PageImpl<>(List.of(d), pageable, 1);
            Set<Long> ids = Set.of(1L);
            List<Object[]> empty = emptyRows();

            when(userRepository.findByRole(User.Role.DRIVER, pageable)).thenReturn(page);
            when(vehicleRepository.findByUserId(1L))
                    .thenReturn(List.of(unavailableVehicle(10L, 1L)));
            when(bookingRepository.countByDriverIdInAndStatus(ids, Booking.BookingStatus.COMPLETED))
                    .thenReturn(empty);
            when(bookingRepository.countByDriverIdIn(ids)).thenReturn(empty);
            when(ratingRepository.findAverageRatingsForDriverIds(ids)).thenReturn(empty);

            Page<DriverResponse> result = driverService.listDrivers(
                    DriverFilterRequest.builder().build(), pageable);

            assertThat(result.getContent().get(0).getIsAvailable()).isFalse();
        }

        @Test
        @DisplayName("isAvailable=false when driver has no vehicles")
        void isAvailableFalseWhenNoVehicles() {
            User d = driver(1L, "Abebe", true, true);
            Page<User> page = new PageImpl<>(List.of(d), pageable, 1);
            Set<Long> ids = Set.of(1L);

            when(userRepository.findByRole(User.Role.DRIVER, pageable)).thenReturn(page);
            stubEmptyStats(ids);

            Page<DriverResponse> result = driverService.listDrivers(
                    DriverFilterRequest.builder().build(), pageable);

            assertThat(result.getContent().get(0).getIsAvailable()).isFalse();
        }

        @Test
        @DisplayName("yearsExperience is always 0 (schema gap)")
        void yearsExperienceAlwaysZero() {
            User d = driver(1L, "Abebe", true, true);
            Page<User> page = new PageImpl<>(List.of(d), pageable, 1);
            Set<Long> ids = Set.of(1L);

            when(userRepository.findByRole(User.Role.DRIVER, pageable)).thenReturn(page);
            stubEmptyStats(ids);

            Page<DriverResponse> result = driverService.listDrivers(
                    DriverFilterRequest.builder().build(), pageable);

            assertThat(result.getContent().get(0).getYearsExperience()).isEqualTo(0);
        }
    }

    // ── DTO safety ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DTO safety")
    class DtoSafety {

        private final Pageable pageable = PageRequest.of(0, 20);

        @Test
        @DisplayName("DriverResponse has no getPassword() method")
        void noPasswordMethod() {
            try {
                DriverResponse.class.getMethod("getPassword");
                throw new AssertionError("DriverResponse must not expose getPassword()");
            } catch (NoSuchMethodException e) {
                // expected
            }
        }

        @Test
        @DisplayName("vehicle projection omits fleetOwnerId")
        void vehicleOmitsFleetOwnerId() {
            User d = driver(1L, "Abebe", true, true);
            Page<User> page = new PageImpl<>(List.of(d), pageable, 1);
            Set<Long> ids = Set.of(1L);
            List<Object[]> empty = emptyRows();

            Vehicle v = availableVehicle(10L, 1L);
            v.setFleetOwnerId(99L);

            when(userRepository.findByRole(User.Role.DRIVER, pageable)).thenReturn(page);
            when(vehicleRepository.findByUserId(1L)).thenReturn(List.of(v));
            when(bookingRepository.countByDriverIdInAndStatus(ids, Booking.BookingStatus.COMPLETED))
                    .thenReturn(empty);
            when(bookingRepository.countByDriverIdIn(ids)).thenReturn(empty);
            when(ratingRepository.findAverageRatingsForDriverIds(ids)).thenReturn(empty);

            Page<DriverResponse> result = driverService.listDrivers(
                    DriverFilterRequest.builder().build(), pageable);

            assertThat(result.getContent().get(0).getVehicles().get(0).getFleetOwnerId()).isNull();
        }

        @Test
        @DisplayName("vehicle projection omits latitude and longitude")
        void vehicleOmitsCoordinates() {
            User d = driver(1L, "Abebe", true, true);
            Page<User> page = new PageImpl<>(List.of(d), pageable, 1);
            Set<Long> ids = Set.of(1L);
            List<Object[]> empty = emptyRows();

            Vehicle v = availableVehicle(10L, 1L);

            when(userRepository.findByRole(User.Role.DRIVER, pageable)).thenReturn(page);
            when(vehicleRepository.findByUserId(1L)).thenReturn(List.of(v));
            when(bookingRepository.countByDriverIdInAndStatus(ids, Booking.BookingStatus.COMPLETED))
                    .thenReturn(empty);
            when(bookingRepository.countByDriverIdIn(ids)).thenReturn(empty);
            when(ratingRepository.findAverageRatingsForDriverIds(ids)).thenReturn(empty);

            Page<DriverResponse> result = driverService.listDrivers(
                    DriverFilterRequest.builder().build(), pageable);

            assertThat(result.getContent().get(0).getVehicles().get(0).getLatitude()).isNull();
            assertThat(result.getContent().get(0).getVehicles().get(0).getLongitude()).isNull();
        }
    }

    // ── Pagination ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Pagination")
    class Pagination {

        @Test
        @DisplayName("pageable is forwarded to repository unmodified")
        void pageableForwarded() {
            Pageable pageable = PageRequest.of(2, 5, Sort.by("fullName"));
            when(userRepository.findByRole(User.Role.DRIVER, pageable))
                    .thenReturn(Page.empty(pageable));

            Page<DriverResponse> result = driverService.listDrivers(
                    DriverFilterRequest.builder().build(), pageable);

            verify(userRepository).findByRole(User.Role.DRIVER, pageable);
            assertThat(result.getNumber()).isEqualTo(2);
            assertThat(result.getSize()).isEqualTo(5);
        }

        @Test
        @DisplayName("empty page — no bulk queries issued")
        void emptyPageSkipsBulkQueries() {
            Pageable pageable = PageRequest.of(0, 20);
            when(userRepository.findByRole(User.Role.DRIVER, pageable))
                    .thenReturn(Page.empty(pageable));

            Page<DriverResponse> result = driverService.listDrivers(
                    DriverFilterRequest.builder().build(), pageable);

            assertThat(result.getContent()).isEmpty();
            verify(bookingRepository, never()).countByDriverIdIn(any());
            verify(ratingRepository, never()).findAverageRatingsForDriverIds(any());
        }
    }
}
