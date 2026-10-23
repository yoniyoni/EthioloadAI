package com.ethioloadai.carrier.dispatch;

import com.ethioloadai.carrier.company.entity.CarrierCompany;
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
import com.ethioloadai.notification.service.NotificationStoreService;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.bid.service.CommissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
        company = CarrierCompany.builder().companyName("Carrier").build();
        setId(company, 10L);

        CompanyMembership membership = new CompanyMembership();
        membership.setRole(CompanyRole.CARRIER_OWNER);
        when(companyContextService.resolveForCompany(10L, owner))
                .thenReturn(new CompanyContext(membership, company));

        freight = new Freight();
        freight.setId(100L);
        freight.setStatus(Freight.FreightStatus.POSTED);
        freight.setCarrierAwardOfferId(1L);
        freight.setWeightTons(BigDecimal.TEN);
        freight.setPickupLocation("Addis Ababa");
        freight.setDestination("Dire Dawa");
        freight.setMaterialType("general cargo");
        when(freightRepo.findByIdForUpdate(100L)).thenReturn(Optional.of(freight));

        award = CarrierOffer.builder()
                .id(1L)
                .carrierCompany(company)
                .status(CarrierOffer.OfferStatus.ACCEPTED)
                .agreedAmount(new BigDecimal("45000.00"))
                .assignmentStatus(CarrierAssignmentStatus.PENDING_ASSIGNMENT)
                .build();
        when(offerRepo.findById(1L)).thenReturn(Optional.of(award));
        lenient().when(offerRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void identicalActiveDispatchRetryReturnsExistingAssignment() {
        DispatchAssignment existing = dispatch(41L, 6L, 200L);
        when(dispatchRepo.findActiveByFreightIdForUpdate(100L)).thenReturn(Optional.of(existing));

        var response = service.createDispatch(100L, 10L, request(6L, 200L), owner);

        assertThat(response.getId()).isEqualTo(41L);
        assertThat(response.getStatus()).isEqualTo(DispatchStatus.ASSIGNED);
        verify(dispatchRepo, never()).saveAndFlush(any());
        verify(offerRepo, never()).save(any());
        verify(driverRepo, never()).save(any());
        verify(vehicleRepo, never()).save(any());
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
