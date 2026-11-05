package com.ethioloadai.carrier.dispatch;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.carrier.company.entity.CarrierCompany;
import com.ethioloadai.carrier.company.entity.VerificationStatus;
import com.ethioloadai.carrier.company.repository.CarrierCompanyRepository;
import com.ethioloadai.carrier.dispatch.dto.CreateDispatchRequest;
import com.ethioloadai.carrier.dispatch.entity.DispatchAssignment;
import com.ethioloadai.carrier.dispatch.entity.DispatchStatus;
import com.ethioloadai.carrier.dispatch.exception.DispatchException;
import com.ethioloadai.carrier.dispatch.repository.DispatchAssignmentRepository;
import com.ethioloadai.carrier.dispatch.service.DispatchServiceImpl;
import com.ethioloadai.carrier.fleet.entity.CarrierDriver;
import com.ethioloadai.carrier.fleet.entity.CarrierDriverStatus;
import com.ethioloadai.carrier.fleet.entity.DriverAvailability;
import com.ethioloadai.carrier.fleet.repository.CarrierDriverRepository;
import com.ethioloadai.carrier.offer.entity.CarrierAssignmentStatus;
import com.ethioloadai.carrier.offer.entity.CarrierOffer;
import com.ethioloadai.carrier.offer.repository.CarrierOfferRepository;
import com.ethioloadai.carrier.team.entity.CompanyMembership;
import com.ethioloadai.carrier.team.entity.CompanyRole;
import com.ethioloadai.carrier.team.service.CompanyContext;
import com.ethioloadai.carrier.team.service.CompanyContextService;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.freight.service.FreightVisibilityProperties;
import com.ethioloadai.notification.service.NotificationStoreService;
import com.ethioloadai.trip.entity.Trip;
import com.ethioloadai.trip.repository.TripRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.bid.service.CommissionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DispatchServiceTest {
    @Mock DispatchAssignmentRepository dispatchRepo;
    @Mock CarrierCompanyRepository companyRepo;
    @Mock FreightRepository freightRepo;
    @Mock CarrierOfferRepository offerRepo;
    @Mock CarrierDriverRepository driverRepo;
    @Mock VehicleRepository vehicleRepo;
    @Mock UserRepository userRepo;
    @Mock BookingRepository bookingRepo;
    @Mock CompanyContextService companyContextService;
    @Mock NotificationStoreService notificationService;
    @Mock CommissionService commissionService;
    @Mock TripRepository tripRepository;
    @Mock FreightVisibilityProperties visibilityProperties;

    @InjectMocks DispatchServiceImpl service;

    private User owner;
    private User driver;
    private CarrierCompany company;
    private Freight freight;
    private CarrierOffer award;

    @BeforeEach
    void setUp() {
        owner = user(2L, User.Role.SHIPPER);
        driver = user(6L, User.Role.DRIVER);
        company = CarrierCompany.builder()
                .companyName("Carrier")
                .verificationStatus(VerificationStatus.APPROVED)
                .build();
        setId(company, 10L);

        CompanyMembership membership = new CompanyMembership();
        membership.setRole(CompanyRole.CARRIER_OWNER);
        lenient().when(companyContextService.resolveForCompany(10L, owner))
                .thenReturn(new CompanyContext(membership, company));

        freight = new Freight();
        freight.setId(100L);
        freight.setStatus(Freight.FreightStatus.POSTED);
        freight.setCarrierAwardOfferId(1L);
        freight.setWeightTons(BigDecimal.TEN);
        freight.setPickupLocation("Addis Ababa");
        freight.setDestination("Dire Dawa");
        freight.setMaterialType("general cargo");
        lenient().when(freightRepo.findByIdForUpdate(100L)).thenReturn(Optional.of(freight));

        award = CarrierOffer.builder()
                .id(1L)
                .carrierCompany(company)
                .status(CarrierOffer.OfferStatus.ACCEPTED)
                .agreedAmount(new BigDecimal("45000.00"))
                .assignmentStatus(CarrierAssignmentStatus.PENDING_ASSIGNMENT)
                .build();
        lenient().when(offerRepo.findById(1L)).thenReturn(Optional.of(award));
        lenient().when(offerRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void identicalActiveDispatchRetryReturnsExistingAssignment() {
        DispatchAssignment existing = dispatch(41L, 6L, 200L);
        when(dispatchRepo.findActiveByFreightIdForUpdate(100L)).thenReturn(Optional.of(existing));

        var response = service.createDispatch(100L, 10L, request(6L, 200L), owner);

        assertThat(response.getId()).isEqualTo(41L);
        assertThat(response.getStatus()).isEqualTo(DispatchStatus.ASSIGNED);
        assertThat(response.getAgreedAmount()).isEqualByComparingTo("45000.00");
        verify(dispatchRepo, never()).saveAndFlush(any());
        verify(offerRepo, never()).save(any());
        verify(driverRepo, never()).save(any());
        verify(vehicleRepo, never()).save(any());
    }

    @Test
    void fleetManagerIdenticalActiveDispatchRetryRetainsAgreedAmount() {
        CompanyMembership membership = new CompanyMembership();
        membership.setRole(CompanyRole.FLEET_MANAGER);
        when(companyContextService.resolveForCompany(10L, owner))
                .thenReturn(new CompanyContext(membership, company));
        DispatchAssignment existing = dispatch(41L, 6L, 200L);
        when(dispatchRepo.findActiveByFreightIdForUpdate(100L)).thenReturn(Optional.of(existing));

        var response = service.createDispatch(100L, 10L, request(6L, 200L), owner);

        assertThat(response.getId()).isEqualTo(41L);
        assertThat(response.getAgreedAmount()).isEqualByComparingTo("45000.00");
        verify(dispatchRepo, never()).saveAndFlush(any());
    }

    @Test
    void dispatcherIdenticalActiveDispatchRetryRedactsAgreedAmount() {
        CompanyMembership membership = new CompanyMembership();
        membership.setRole(CompanyRole.DISPATCHER);
        when(companyContextService.resolveForCompany(10L, owner))
                .thenReturn(new CompanyContext(membership, company));
        DispatchAssignment existing = dispatch(41L, 6L, 200L);
        when(dispatchRepo.findActiveByFreightIdForUpdate(100L)).thenReturn(Optional.of(existing));

        var response = service.createDispatch(100L, 10L, request(6L, 200L), owner);

        assertThat(response.getId()).isEqualTo(41L);
        assertThat(response.getAgreedAmount()).isNull();
        verify(dispatchRepo, never()).saveAndFlush(any());
    }

    @Test
    void dispatchCreationRejectsUnapprovedCompanyInServiceLayer() {
        company.setVerificationStatus(VerificationStatus.PENDING);

        assertThatThrownBy(() -> service.createDispatch(100L, 10L, request(6L, 200L), owner))
                .isInstanceOf(com.ethioloadai.carrier.team.exception.CompanyTeamException.class)
                .hasMessageContaining("approval");
        verify(freightRepo, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void dispatchCancellationRejectsUnapprovedCompanyInServiceLayer() {
        company.setVerificationStatus(VerificationStatus.PENDING);

        assertThatThrownBy(() -> service.cancelDispatch(41L, 10L, owner))
                .isInstanceOf(com.ethioloadai.carrier.team.exception.CompanyTeamException.class)
                .hasMessageContaining("approval");
        verify(dispatchRepo, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void dispatcherCompanyDispatchReadRedactsAgreedAmount() {
        CompanyMembership membership = new CompanyMembership();
        membership.setRole(CompanyRole.DISPATCHER);
        when(companyContextService.resolveForCompany(10L, owner))
                .thenReturn(new CompanyContext(membership, company));
        when(dispatchRepo.findByIdAndCarrierCompanyId(41L, 10L))
                .thenReturn(Optional.of(dispatch(41L, 6L, 200L)));
        when(freightRepo.findById(100L)).thenReturn(Optional.of(freight));

        var response = service.getDispatch(41L, 10L, owner);

        assertThat(response.getAgreedAmount()).isNull();
        assertThat(response.getCarrierCompanyId()).isEqualTo(10L);
    }

    @Test
    void awardedFreightListOmitsCompletedFreightOutsideCarrierHistoryWindow() {
        freight.setStatus(Freight.FreightStatus.COMPLETED);
        award.setFreight(freight);
        when(offerRepo.findAcceptedByCompanyId(10L)).thenReturn(List.of(award));
        when(tripRepository.findByFreightIdOrderByEndTimeDesc(100L)).thenReturn(List.of(
                Trip.builder().tripStatus(Trip.TripStatus.COMPLETED)
                        .endTime(LocalDateTime.now().minusDays(91)).build()));
        when(visibilityProperties.getCarrierHistoryDays()).thenReturn(90L);

        assertThat(service.getAwardedFreights(10L, owner)).isEmpty();
    }

    @Test
    void companyDispatchPageUsesDatabaseFilteredCarrierHistory() {
        when(visibilityProperties.getCarrierHistoryDays()).thenReturn(90L);
        when(dispatchRepo.findVisibleByCarrierCompanyId(
                eq(10L), anyList(), eq(Freight.FreightStatus.CANCELLED),
                eq(Freight.FreightStatus.COMPLETED), eq(Trip.TripStatus.COMPLETED),
                eq(List.of(Booking.BookingStatus.CANCELLED, Booking.BookingStatus.COMPLETED)),
                any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(Page.empty(Pageable.unpaged()));

        service.listCompanyDispatches(10L, owner, Pageable.unpaged());

        verify(dispatchRepo).findVisibleByCarrierCompanyId(
                eq(10L), anyList(), eq(Freight.FreightStatus.CANCELLED),
                eq(Freight.FreightStatus.COMPLETED), eq(Trip.TripStatus.COMPLETED),
                eq(List.of(Booking.BookingStatus.CANCELLED, Booking.BookingStatus.COMPLETED)),
                any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class));
        verify(dispatchRepo, never()).findByCarrierCompanyIdOrderByAssignedAtDesc(
                anyLong(), any(Pageable.class));
    }

    @Test
    void companyDispatchListReturnsTheRepositoryFilteredPageForNullCompletedTripRecords() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<DispatchAssignment> filteredPage = Page.empty(pageable);
        when(visibilityProperties.getCarrierHistoryDays()).thenReturn(90L);
        when(dispatchRepo.findVisibleByCarrierCompanyId(
                eq(10L), anyList(), eq(Freight.FreightStatus.CANCELLED),
                eq(Freight.FreightStatus.COMPLETED), eq(Trip.TripStatus.COMPLETED),
                eq(List.of(Booking.BookingStatus.CANCELLED, Booking.BookingStatus.COMPLETED)),
                any(LocalDateTime.class), any(LocalDateTime.class), eq(pageable)))
                .thenReturn(filteredPage);

        Page<?> result = service.listCompanyDispatches(10L, owner, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        verify(dispatchRepo).findVisibleByCarrierCompanyId(
                eq(10L), anyList(), eq(Freight.FreightStatus.CANCELLED),
                eq(Freight.FreightStatus.COMPLETED), eq(Trip.TripStatus.COMPLETED),
                eq(List.of(Booking.BookingStatus.CANCELLED, Booking.BookingStatus.COMPLETED)),
                any(LocalDateTime.class), any(LocalDateTime.class), eq(pageable));
    }

    @Test
    void driverAssignmentInboxUsesConfiguredHistoryFilteredQuery() {
        when(visibilityProperties.getDriverHistoryDays()).thenReturn(30L);
        when(dispatchRepo.findVisibleByDriverUserId(
                eq(6L), anyList(), eq(DispatchStatus.DRIVER_ACCEPTED), anyList(),
                eq(Freight.FreightStatus.CANCELLED), eq(Freight.FreightStatus.COMPLETED),
                eq(Trip.TripStatus.COMPLETED),
                eq(List.of(Booking.BookingStatus.CANCELLED, Booking.BookingStatus.COMPLETED)),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        assertThat(service.getMyAssignments(driver)).isEmpty();

        verify(dispatchRepo).findVisibleByDriverUserId(
                eq(6L), anyList(), eq(DispatchStatus.DRIVER_ACCEPTED), anyList(),
                eq(Freight.FreightStatus.CANCELLED), eq(Freight.FreightStatus.COMPLETED),
                eq(Trip.TripStatus.COMPLETED),
                eq(List.of(Booking.BookingStatus.CANCELLED, Booking.BookingStatus.COMPLETED)),
                any(LocalDateTime.class), any(LocalDateTime.class));
        verify(dispatchRepo, never()).findByDriverUserIdOrderByAssignedAtDesc(6L);
    }

    @Test
    void driverAssignmentListUsesRepositoryNullCompletedTripFilter() {
        when(visibilityProperties.getDriverHistoryDays()).thenReturn(30L);
        when(dispatchRepo.findVisibleByDriverUserId(
                eq(6L), anyList(), eq(DispatchStatus.DRIVER_ACCEPTED), anyList(),
                eq(Freight.FreightStatus.CANCELLED), eq(Freight.FreightStatus.COMPLETED),
                eq(Trip.TripStatus.COMPLETED),
                eq(List.of(Booking.BookingStatus.CANCELLED, Booking.BookingStatus.COMPLETED)),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        assertThat(service.getMyAssignments(driver)).isEmpty();
        verify(dispatchRepo).findVisibleByDriverUserId(
                eq(6L), anyList(), eq(DispatchStatus.DRIVER_ACCEPTED), anyList(),
                eq(Freight.FreightStatus.CANCELLED), eq(Freight.FreightStatus.COMPLETED),
                eq(Trip.TripStatus.COMPLETED),
                eq(List.of(Booking.BookingStatus.CANCELLED, Booking.BookingStatus.COMPLETED)),
                any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void expiredCompanyDispatchDetailIsNotReturned() {
        DispatchAssignment assignment = dispatch(41L, 6L, 200L);
        freight.setStatus(Freight.FreightStatus.COMPLETED);
        when(dispatchRepo.findByIdAndCarrierCompanyId(41L, 10L)).thenReturn(Optional.of(assignment));
        when(freightRepo.findById(100L)).thenReturn(Optional.of(freight));
        when(tripRepository.findByFreightIdOrderByEndTimeDesc(100L)).thenReturn(List.of(
                Trip.builder().tripStatus(Trip.TripStatus.COMPLETED)
                        .endTime(LocalDateTime.now().minusDays(91)).build()));
        when(visibilityProperties.getCarrierHistoryDays()).thenReturn(90L);

        assertThatThrownBy(() -> service.getDispatch(41L, 10L, owner))
                .isInstanceOf(DispatchException.class);
    }

    @Test
    void completedTripExpiresCarrierDispatchEvenWhenFreightStatusIsStale() {
        DispatchAssignment assignment = dispatch(42L, 6L, 200L);
        when(dispatchRepo.findByIdAndCarrierCompanyId(42L, 10L)).thenReturn(Optional.of(assignment));
        when(freightRepo.findById(100L)).thenReturn(Optional.of(freight));
        when(tripRepository.findByFreightIdOrderByEndTimeDesc(100L)).thenReturn(List.of(
                Trip.builder().tripStatus(Trip.TripStatus.COMPLETED)
                        .endTime(LocalDateTime.now().minusDays(91)).build()));
        when(visibilityProperties.getCarrierHistoryDays()).thenReturn(90L);

        assertThatThrownBy(() -> service.getDispatch(42L, 10L, owner))
                .isInstanceOf(DispatchException.class);
    }

    @Test
    void completedTripWithNullEndTimeDeniesCarrierDetailEvenWhenFreightStatusIsStale() {
        DispatchAssignment assignment = dispatch(45L, 6L, 200L);
        when(dispatchRepo.findByIdAndCarrierCompanyId(45L, 10L)).thenReturn(Optional.of(assignment));
        when(freightRepo.findById(100L)).thenReturn(Optional.of(freight));
        when(tripRepository.findByFreightIdOrderByEndTimeDesc(100L)).thenReturn(List.of(
                Trip.builder().tripStatus(Trip.TripStatus.COMPLETED).endTime(null).build()));

        assertThatThrownBy(() -> service.getDispatch(45L, 10L, owner))
                .isInstanceOf(DispatchException.class);
    }

    @Test
    void completedTripWithinCarrierWindowRetainsDetail() {
        DispatchAssignment assignment = dispatch(46L, 6L, 200L);
        when(dispatchRepo.findByIdAndCarrierCompanyId(46L, 10L)).thenReturn(Optional.of(assignment));
        when(freightRepo.findById(100L)).thenReturn(Optional.of(freight));
        when(tripRepository.findByFreightIdOrderByEndTimeDesc(100L)).thenReturn(List.of(
                Trip.builder().tripStatus(Trip.TripStatus.COMPLETED)
                        .endTime(LocalDateTime.now().minusDays(89)).build()));
        when(visibilityProperties.getCarrierHistoryDays()).thenReturn(90L);

        assertThat(service.getDispatch(46L, 10L, owner).getId()).isEqualTo(46L);
    }

    @Test
    void completedTripWithNullEndTimeDeniesDriverHistoryAndDetailQueryIsScoped() {
        when(dispatchRepo.findVisibleByIdAndDriverUserId(
                eq(47L), eq(driver.getId()), anyList(), eq(DispatchStatus.DRIVER_ACCEPTED),
                anyList(), eq(Freight.FreightStatus.CANCELLED), eq(Freight.FreightStatus.COMPLETED),
                eq(Trip.TripStatus.COMPLETED),
                eq(List.of(Booking.BookingStatus.CANCELLED, Booking.BookingStatus.COMPLETED)),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyAssignment(47L, driver))
                .isInstanceOf(DispatchException.class);
        verify(dispatchRepo).findVisibleByIdAndDriverUserId(
                eq(47L), eq(driver.getId()), anyList(), eq(DispatchStatus.DRIVER_ACCEPTED),
                anyList(), eq(Freight.FreightStatus.CANCELLED), eq(Freight.FreightStatus.COMPLETED),
                eq(Trip.TripStatus.COMPLETED),
                eq(List.of(Booking.BookingStatus.CANCELLED, Booking.BookingStatus.COMPLETED)),
                any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void nullCompletedTripIsRejectedByDispatchListAndCountPredicates() throws Exception {
        assertNullCompletedTripGuard("findVisibleByCarrierCompanyId");
        assertNullCompletedTripGuard("findVisibleByDriverUserId");
        assertNullCompletedTripGuard("findVisibleByIdAndDriverUserId");

        Method companyPageMethod = java.util.Arrays.stream(DispatchAssignmentRepository.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("findVisibleByCarrierCompanyId"))
                .findFirst()
                .orElseThrow();
        org.springframework.data.jpa.repository.Query query =
                companyPageMethod.getAnnotation(org.springframework.data.jpa.repository.Query.class);
        assertThat(query.countQuery()).isBlank();
        assertThat(companyPageMethod.getReturnType()).isEqualTo(Page.class);
    }

    @Test
    void companyDispatchPagePreservesFilteredPageTotals() {
        Pageable pageable = PageRequest.of(0, 1);
        DispatchAssignment visible = dispatch(48L, 6L, 200L);
        when(visibilityProperties.getCarrierHistoryDays()).thenReturn(90L);
        when(dispatchRepo.findVisibleByCarrierCompanyId(
                eq(10L), anyList(), eq(Freight.FreightStatus.CANCELLED),
                eq(Freight.FreightStatus.COMPLETED), eq(Trip.TripStatus.COMPLETED),
                eq(List.of(Booking.BookingStatus.CANCELLED, Booking.BookingStatus.COMPLETED)),
                any(LocalDateTime.class), any(LocalDateTime.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(visible), pageable, 4));

        Page<?> result = service.listCompanyDispatches(10L, owner, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(4);
    }

    private void assertNullCompletedTripGuard(String methodName) {
        Method method = java.util.Arrays.stream(DispatchAssignmentRepository.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        String query = method.getAnnotation(org.springframework.data.jpa.repository.Query.class).value();
        assertThat(query).contains("tripStatus = :completedTripStatus AND invalidTrip.endTime IS NULL");
    }

    @Test
    void terminalBookingExpiresCarrierDispatchWhenNoCompletedTripExists() {
        DispatchAssignment assignment = dispatch(43L, 6L, 200L);
        when(dispatchRepo.findByIdAndCarrierCompanyId(43L, 10L)).thenReturn(Optional.of(assignment));
        when(freightRepo.findById(100L)).thenReturn(Optional.of(freight));
        when(bookingRepo.findByCargoRequest_Id(100L)).thenReturn(List.of(
                Booking.builder().bookingStatus(Booking.BookingStatus.COMPLETED)
                        .updatedAt(LocalDateTime.now().minusDays(91)).build()));
        when(visibilityProperties.getCarrierHistoryDays()).thenReturn(90L);

        assertThatThrownBy(() -> service.getDispatch(43L, 10L, owner))
                .isInstanceOf(DispatchException.class);
    }

    @Test
    void zeroDayCarrierHistoryDeniesCompletedTripForStaleFreight() {
        DispatchAssignment assignment = dispatch(44L, 6L, 200L);
        when(dispatchRepo.findByIdAndCarrierCompanyId(44L, 10L)).thenReturn(Optional.of(assignment));
        when(freightRepo.findById(100L)).thenReturn(Optional.of(freight));
        when(tripRepository.findByFreightIdOrderByEndTimeDesc(100L)).thenReturn(List.of(
                Trip.builder().tripStatus(Trip.TripStatus.COMPLETED)
                        .endTime(LocalDateTime.now()).build()));
        when(visibilityProperties.getCarrierHistoryDays()).thenReturn(0L);

        assertThatThrownBy(() -> service.getDispatch(44L, 10L, owner))
                .isInstanceOf(DispatchException.class);
    }

    @Test
    void conflictingActiveDispatchCannotCreateAnotherAssignment() {
        when(dispatchRepo.findActiveByFreightIdForUpdate(100L))
                .thenReturn(Optional.of(dispatch(41L, 7L, 201L)));

        assertThatThrownBy(() -> service.createDispatch(100L, 10L, request(6L, 200L), owner))
                .isInstanceOf(DispatchException.class)
                .hasMessageContaining("already has an active dispatch");

        verify(dispatchRepo, never()).saveAndFlush(any());
        verify(driverRepo, never()).save(any());
        verify(vehicleRepo, never()).save(any());
    }

    @Test
    void eligibleReplacementCreatesDispatchAndMarksAssignmentPending() {
        when(dispatchRepo.findActiveByFreightIdForUpdate(100L)).thenReturn(Optional.empty());
        CarrierDriver carrierDriver = CarrierDriver.builder()
                .id(300L)
                .carrierCompanyId(10L)
                .driverUserId(6L)
                .status(CarrierDriverStatus.ACTIVE)
                .availability(DriverAvailability.AVAILABLE)
                .build();
        Vehicle vehicle = Vehicle.builder()
                .id(200L)
                .carrierCompanyId(10L)
                .truckType("Heavy truck")
                .plateNumber("TEST-200")
                .capacity(20.0)
                .availabilityStatus("available")
                .approvalStatus(Vehicle.VehicleApprovalStatus.APPROVED)
                .build();
        when(driverRepo.findByCarrierCompanyIdAndDriverUserIdForUpdate(10L, 6L))
                .thenReturn(Optional.of(carrierDriver));
        when(userRepo.findById(6L)).thenReturn(Optional.of(driver));
        when(vehicleRepo.findByIdAndCarrierCompanyIdForUpdate(200L, 10L))
                .thenReturn(Optional.of(vehicle));
        when(dispatchRepo.findPendingByDriverUserId(6L)).thenReturn(Optional.empty());
        when(dispatchRepo.findPendingByVehicleId(200L)).thenReturn(Optional.empty());
        when(dispatchRepo.saveAndFlush(any())).thenAnswer(invocation -> {
            DispatchAssignment saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        var response = service.createDispatch(100L, 10L, request(6L, 200L), owner);

        assertThat(response.getStatus()).isEqualTo(DispatchStatus.ASSIGNED);
        assertThat(response.getAgreedAmount()).isEqualByComparingTo("45000.00");
        assertThat(award.getAssignmentStatus()).isEqualTo(CarrierAssignmentStatus.DISPATCH_PENDING);
        assertThat(carrierDriver.getAvailability()).isEqualTo(DriverAvailability.ASSIGNED);
        assertThat(vehicle.getAvailabilityStatus()).isEqualTo("assigned");
    }

    @Test
    void fleetManagerCanCreateOperationalDispatch() {
        CompanyMembership membership = new CompanyMembership();
        membership.setRole(CompanyRole.FLEET_MANAGER);
        when(companyContextService.resolveForCompany(10L, owner))
                .thenReturn(new CompanyContext(membership, company));
        when(dispatchRepo.findActiveByFreightIdForUpdate(100L)).thenReturn(Optional.empty());
        CarrierDriver carrierDriver = CarrierDriver.builder()
                .id(300L)
                .carrierCompanyId(10L)
                .driverUserId(6L)
                .status(CarrierDriverStatus.ACTIVE)
                .availability(DriverAvailability.AVAILABLE)
                .build();
        Vehicle vehicle = Vehicle.builder()
                .id(200L)
                .carrierCompanyId(10L)
                .truckType("Heavy truck")
                .plateNumber("TEST-200")
                .capacity(20.0)
                .availabilityStatus("available")
                .approvalStatus(Vehicle.VehicleApprovalStatus.APPROVED)
                .build();
        when(driverRepo.findByCarrierCompanyIdAndDriverUserIdForUpdate(10L, 6L))
                .thenReturn(Optional.of(carrierDriver));
        when(userRepo.findById(6L)).thenReturn(Optional.of(driver));
        when(vehicleRepo.findByIdAndCarrierCompanyIdForUpdate(200L, 10L))
                .thenReturn(Optional.of(vehicle));
        when(dispatchRepo.findPendingByDriverUserId(6L)).thenReturn(Optional.empty());
        when(dispatchRepo.findPendingByVehicleId(200L)).thenReturn(Optional.empty());
        when(dispatchRepo.saveAndFlush(any())).thenAnswer(invocation -> {
            DispatchAssignment saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        var response = service.createDispatch(100L, 10L, request(6L, 200L), owner);

        assertThat(response.getStatus()).isEqualTo(DispatchStatus.ASSIGNED);
        assertThat(response.getAgreedAmount()).isEqualByComparingTo("45000.00");
        assertThat(award.getAssignmentStatus()).isEqualTo(CarrierAssignmentStatus.DISPATCH_PENDING);
    }

    @Test
    void ineligibleReplacementLeavesAwardAndResourcesUnchanged() {
        when(dispatchRepo.findActiveByFreightIdForUpdate(100L)).thenReturn(Optional.empty());
        CarrierDriver carrierDriver = CarrierDriver.builder()
                .id(300L)
                .carrierCompanyId(10L)
                .driverUserId(6L)
                .status(CarrierDriverStatus.ACTIVE)
                .availability(DriverAvailability.AVAILABLE)
                .build();
        Vehicle ineligibleVehicle = Vehicle.builder()
                .id(200L)
                .carrierCompanyId(10L)
                .truckType("Heavy truck")
                .plateNumber("STALE-200")
                .capacity(20.0)
                .availabilityStatus("available")
                .approvalStatus(Vehicle.VehicleApprovalStatus.UNDER_REVIEW)
                .build();
        when(driverRepo.findByCarrierCompanyIdAndDriverUserIdForUpdate(10L, 6L))
                .thenReturn(Optional.of(carrierDriver));
        when(userRepo.findById(6L)).thenReturn(Optional.of(driver));
        when(vehicleRepo.findByIdAndCarrierCompanyIdForUpdate(200L, 10L))
                .thenReturn(Optional.of(ineligibleVehicle));

        assertThatThrownBy(() -> service.createDispatch(100L, 10L, request(6L, 200L), owner))
                .isInstanceOf(DispatchException.class)
                .hasMessageContaining("not APPROVED");

        assertThat(award.getStatus()).isEqualTo(CarrierOffer.OfferStatus.ACCEPTED);
        assertThat(award.getAgreedAmount()).isEqualByComparingTo("45000.00");
        assertThat(award.getAssignmentStatus()).isEqualTo(CarrierAssignmentStatus.PENDING_ASSIGNMENT);
        assertThat(carrierDriver.getAvailability()).isEqualTo(DriverAvailability.AVAILABLE);
        assertThat(ineligibleVehicle.getAvailabilityStatus()).isEqualTo("available");
        verify(dispatchRepo, never()).saveAndFlush(any());
        verify(offerRepo, never()).save(any());
        verify(driverRepo, never()).save(any());
        verify(vehicleRepo, never()).save(any());
    }

    @Test
    void cancellingPendingDispatchPreservesAwardAndReopensAssignment() {
        DispatchAssignment assignment = dispatch(41L, 6L, 200L);
        when(dispatchRepo.findFreightIdById(41L)).thenReturn(Optional.of(100L));
        when(dispatchRepo.findByIdForUpdate(41L)).thenReturn(Optional.of(assignment));
        when(dispatchRepo.save(assignment)).thenReturn(assignment);

        var response = service.cancelDispatch(41L, 10L, owner);

        assertThat(response.getStatus()).isEqualTo(DispatchStatus.CANCELLED);
        assertThat(award.getStatus()).isEqualTo(CarrierOffer.OfferStatus.ACCEPTED);
        assertThat(award.getAgreedAmount()).isEqualByComparingTo("45000.00");
        assertThat(award.getAssignmentStatus()).isEqualTo(CarrierAssignmentStatus.PENDING_ASSIGNMENT);
        verify(offerRepo).save(award);
    }

    private DispatchAssignment dispatch(Long id, Long driverId, Long vehicleId) {
        return DispatchAssignment.builder()
                .id(id)
                .freightId(100L)
                .carrierOfferId(1L)
                .carrierCompanyId(10L)
                .driverUserId(driverId)
                .vehicleId(vehicleId)
                .assignedBy(2L)
                .status(DispatchStatus.ASSIGNED)
                .agreedAmount(new BigDecimal("45000.00"))
                .build();
    }

    private CreateDispatchRequest request(Long driverId, Long vehicleId) {
        CreateDispatchRequest request = new CreateDispatchRequest();
        request.setDriverUserId(driverId);
        request.setVehicleId(vehicleId);
        return request;
    }

    private User user(Long id, User.Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setIsActive(true);
        return user;
    }

    private void setId(CarrierCompany carrierCompany, Long id) {
        try {
            var field = CarrierCompany.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(carrierCompany, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
