package com.ethioloadai.bid;

import com.ethioloadai.bid.entity.Bid;
import com.ethioloadai.bid.exception.BidStatusException;
import com.ethioloadai.bid.exception.BidValidationException;
import com.ethioloadai.bid.repository.BidRepository;
import com.ethioloadai.bid.service.BidServiceImpl;
import com.ethioloadai.exception.AuthorizationException;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BidServiceUnitTest {

    @Mock
    private BidRepository bidRepository;

    @Mock
    private FreightRepository freightRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BidServiceImpl bidService;

    private User shipper;
    private User driver;
    private User fleetOwner;
    private User admin;
    private Freight freight;
    private Vehicle driverVehicle;
    private Vehicle fleetVehicle;

    @BeforeEach
    void setUp() {
        shipper = User.builder()
                .id(1L)
                .fullName("Test Shipper")
                .phone("1234567890")
                .role(User.Role.SHIPPER)
                .build();

        driver = User.builder()
                .id(2L)
                .fullName("Test Driver")
                .phone("1234567891")
                .role(User.Role.DRIVER)
                .verificationStatus(true)
                .isActive(true)
                .build();

        fleetOwner = User.builder()
                .id(3L)
                .fullName("Fleet Owner")
                .phone("1234567892")
                .role(User.Role.FLEET_OWNER)
                .verificationStatus(true)
                .isActive(true)
                .build();

        admin = User.builder()
                .id(4L)
                .fullName("Admin User")
                .phone("1234567893")
                .role(User.Role.ADMIN)
                .build();

        freight = Freight.builder()
                .id(1L)
                .shipper(shipper)
                .status(Freight.FreightStatus.POSTED)
                .priceType(Freight.PriceType.NEGOTIABLE)
                .serviceType(Freight.ServiceType.INTERCITY)
                .bidDeadline(null)
                .build();

        driverVehicle = Vehicle.builder()
                .id(1L)
                .plateNumber("ABC-001")
                .userId(driver.getId())
                .fleetOwnerId(null)
                .availabilityStatus("available")
                .approvalStatus(Vehicle.VehicleApprovalStatus.APPROVED)
                .build();

        fleetVehicle = Vehicle.builder()
                .id(2L)
                .plateNumber("FLEET-001")
                .userId(null)
                .fleetOwnerId(fleetOwner.getId())
                .availabilityStatus("available")
                .approvalStatus(Vehicle.VehicleApprovalStatus.APPROVED)
                .build();

            lenient().when(userRepository.findById(driver.getId())).thenReturn(Optional.of(driver));
    }

    @Test
    @DisplayName("Should validate status transitions correctly")
    void testValidStatusTransitions() {
        // PENDING transitions
        assertThat(bidService.isValidStatusTransition(Bid.BidStatus.PENDING, Bid.BidStatus.ACCEPTED)).isTrue();
        assertThat(bidService.isValidStatusTransition(Bid.BidStatus.PENDING, Bid.BidStatus.REJECTED)).isTrue();
        assertThat(bidService.isValidStatusTransition(Bid.BidStatus.PENDING, Bid.BidStatus.COUNTERED)).isTrue();
        assertThat(bidService.isValidStatusTransition(Bid.BidStatus.PENDING, Bid.BidStatus.EXPIRED)).isTrue();

        // COUNTERED transitions
        assertThat(bidService.isValidStatusTransition(Bid.BidStatus.COUNTERED, Bid.BidStatus.ACCEPTED)).isTrue();
        assertThat(bidService.isValidStatusTransition(Bid.BidStatus.COUNTERED, Bid.BidStatus.REJECTED)).isTrue();
        assertThat(bidService.isValidStatusTransition(Bid.BidStatus.COUNTERED, Bid.BidStatus.COUNTERED)).isTrue();

        // Final states (should not allow any transitions)
        assertThat(bidService.isValidStatusTransition(Bid.BidStatus.ACCEPTED, Bid.BidStatus.PENDING)).isFalse();
        assertThat(bidService.isValidStatusTransition(Bid.BidStatus.REJECTED, Bid.BidStatus.PENDING)).isFalse();
        assertThat(bidService.isValidStatusTransition(Bid.BidStatus.EXPIRED, Bid.BidStatus.PENDING)).isFalse();
    }

    @Test
    @DisplayName("Should create bid with valid data")
    void testCreateBid_Success() {
        // Given
        when(freightRepository.findById(1L)).thenReturn(Optional.of(freight));
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(driverVehicle));
        when(bidRepository.existsByCargoRequest_IdAndVehicle_Id(1L, 1L)).thenReturn(false);
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> {
            Bid bid = invocation.getArgument(0);
            bid.setId(1L);
            return bid;
        });

        // When
        Bid result = bidService.createBid(
                1L, driver, 1L, new BigDecimal("1000.00"), "Test bid", null
        );

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(result.getStatus()).isEqualTo(Bid.BidStatus.PENDING);
        verify(bidRepository).save(any(Bid.class));
    }

    @Test
    @DisplayName("Should reject bid on non-posted freight")
    void testCreateBid_FreightNotPosted() {
        // Given
        freight.setStatus(Freight.FreightStatus.MATCHED);
        when(freightRepository.findById(1L)).thenReturn(Optional.of(freight));

        // When & Then
        assertThatThrownBy(() -> 
            bidService.createBid(1L, driver, 1L, new BigDecimal("1000.00"), "Test", null)
        ).isInstanceOf(BidValidationException.class)
         .hasMessageContaining("not accepting bids");
    }

    @Test
    @DisplayName("Should reject bid after deadline")
    void testCreateBid_DeadlinePassed() {
        // Given
        freight.setBidDeadline(LocalDateTime.now().minusHours(1));
        when(freightRepository.findById(1L)).thenReturn(Optional.of(freight));

        // When & Then
        assertThatThrownBy(() -> 
            bidService.createBid(1L, driver, 1L, new BigDecimal("1000.00"), "Late bid", null)
        ).isInstanceOf(BidValidationException.class)
         .hasMessageContaining("deadline has passed");
    }

    @Test
    @DisplayName("Should reject bid on fixed-price freight with mismatched amount")
    void testCreateBid_FixedPrice_MismatchedAmount() {
        // Given
        freight.setPriceType(Freight.PriceType.FIXED);
        freight.setBudget(new BigDecimal("6000.00"));
        when(freightRepository.findById(1L)).thenReturn(Optional.of(freight));
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(driverVehicle));

        // When & Then - bidding 5000 on fixed 6000 fails
        assertThatThrownBy(() -> 
            bidService.createBid(1L, driver, 1L, new BigDecimal("5000.00"), "Fixed bid", null)
        ).isInstanceOf(BidValidationException.class)
         .hasMessageContaining("Fixed-price freight requires the exact posted price");
    }

    @Test
    @DisplayName("Should allow application on fixed-price freight with exact posted amount")
    void testCreateBid_FixedPrice_ExactAmount() {
        // Given
        freight.setPriceType(Freight.PriceType.FIXED);
        freight.setBudget(new BigDecimal("6000.00"));
        when(freightRepository.findById(1L)).thenReturn(Optional.of(freight));
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(driverVehicle));
        when(bidRepository.existsByCargoRequest_IdAndVehicle_Id(1L, 1L)).thenReturn(false);
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Bid bid = bidService.createBid(1L, driver, 1L, new BigDecimal("6000.00"), "Fixed price application", null);

        // Then
        assertThat(bid).isNotNull();
        assertThat(bid.getAmount()).isEqualByComparingTo(new BigDecimal("6000.00"));
        assertThat(bid.getStatus()).isEqualTo(Bid.BidStatus.PENDING);
    }

    @Test
    @DisplayName("Should allow bids lower, equal, or higher than budget on negotiable freight")
    void testCreateBid_Negotiable_FlexiblePricing() {
        // Given
        freight.setPriceType(Freight.PriceType.NEGOTIABLE);
        freight.setBudget(new BigDecimal("6000.00"));
        when(freightRepository.findById(1L)).thenReturn(Optional.of(freight));
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(driverVehicle));
        when(bidRepository.existsByCargoRequest_IdAndVehicle_Id(1L, 1L)).thenReturn(false);
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Lower than budget
        Bid lowerBid = bidService.createBid(1L, driver, 1L, new BigDecimal("5000.00"), "Lower bid", null);
        assertThat(lowerBid.getAmount()).isEqualByComparingTo(new BigDecimal("5000.00"));

        // Higher than budget
        Bid higherBid = bidService.createBid(1L, driver, 1L, new BigDecimal("7000.00"), "Higher bid", null);
        assertThat(higherBid.getAmount()).isEqualByComparingTo(new BigDecimal("7000.00"));
    }

    @Test
    @DisplayName("Should reject counter-offer on fixed-price freight")
    void testCreateCounterOffer_FixedPrice() {
        // Given
        freight.setPriceType(Freight.PriceType.FIXED);
        Bid fixedBid = Bid.builder()
                .id(1L)
                .cargoRequest(freight)
                .driver(driver)
                .status(Bid.BidStatus.PENDING)
                .amount(new BigDecimal("1000.00"))
                .build();

        // When & Then
        assertThatThrownBy(() ->
            bidService.createCounterOffer(fixedBid, shipper, new BigDecimal("900.00"), "Negotiate")
        ).isInstanceOf(BidValidationException.class)
         .hasMessageContaining("Counter-offers are not allowed on fixed-price freight");
    }

    @Test
    @DisplayName("Should reject an unverified driver before creating a bid")
    void testCreateBid_UnverifiedDriver() {
        driver.setVerificationStatus(false);
        when(freightRepository.findById(1L)).thenReturn(Optional.of(freight));
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(driverVehicle));

        assertThatThrownBy(() ->
                bidService.createBid(1L, driver, 1L, new BigDecimal("1000.00"), "Pending", null)
        ).isInstanceOf(BidValidationException.class)
         .hasMessageContaining("not verified");

        verify(bidRepository, never()).save(any(Bid.class));
    }

    @Test
    @DisplayName("Should reject a vehicle that is not available for bidding")
    void testCreateBid_UnavailableVehicle() {
        driverVehicle.setAvailabilityStatus("pending");
        when(freightRepository.findById(1L)).thenReturn(Optional.of(freight));
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(driverVehicle));

        assertThatThrownBy(() ->
                bidService.createBid(1L, driver, 1L, new BigDecimal("1000.00"), "Pending", null)
        ).isInstanceOf(BidValidationException.class)
         .hasMessageContaining("not available for bidding");

        verify(bidRepository, never()).save(any(Bid.class));
    }

    @Test
    @DisplayName("Should reject a vehicle that is under review")
    void testCreateBid_UnderReviewVehicle() {
        driverVehicle.setApprovalStatus(Vehicle.VehicleApprovalStatus.UNDER_REVIEW);
        when(freightRepository.findById(1L)).thenReturn(Optional.of(freight));
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(driverVehicle));

        assertThatThrownBy(() -> bidService.createBid(1L, driver, 1L, new BigDecimal("1000.00"), "Pending", null))
                .isInstanceOf(BidValidationException.class)
                .hasMessageContaining("only APPROVED vehicles may bid");
    }

    @Test
    @DisplayName("Should reject rejected and suspended vehicles")
    void testCreateBid_RejectedOrSuspendedVehicle() {
        when(freightRepository.findById(1L)).thenReturn(Optional.of(freight));
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(driverVehicle));
        for (Vehicle.VehicleApprovalStatus status : List.of(Vehicle.VehicleApprovalStatus.REJECTED, Vehicle.VehicleApprovalStatus.SUSPENDED)) {
            driverVehicle.setApprovalStatus(status);
            assertThatThrownBy(() -> bidService.createBid(1L, driver, 1L, new BigDecimal("1000.00"), "Blocked", null))
                    .isInstanceOf(BidValidationException.class)
                    .hasMessageContaining("only APPROVED vehicles may bid");
        }
    }

    @Test
    @DisplayName("Should validate driver vehicle ownership")
    void testCreateBid_InvalidVehicleOwnership() {
        // Given - driver tries to bid with vehicle they don't own
        Vehicle otherVehicle = Vehicle.builder()
                .id(999L)
                .plateNumber("OTHER-001")
                .userId(999L) // Different user
                .approvalStatus(Vehicle.VehicleApprovalStatus.APPROVED)
                .build();

        when(freightRepository.findById(1L)).thenReturn(Optional.of(freight));
        when(vehicleRepository.findById(999L)).thenReturn(Optional.of(otherVehicle));

        // When & Then
        assertThatThrownBy(() -> 
            bidService.createBid(1L, driver, 999L, new BigDecimal("1000.00"), "Invalid", null)
        ).isInstanceOf(BidValidationException.class)
         .hasMessageContaining("not owned by the driver");
    }

    @Test
    @DisplayName("Should validate fleet owner vehicle ownership")
    void testCreateBid_FleetVehicleOwnership() {
        // Given
        when(freightRepository.findById(1L)).thenReturn(Optional.of(freight));
        when(vehicleRepository.findById(2L)).thenReturn(Optional.of(fleetVehicle));
        when(bidRepository.existsByCargoRequest_IdAndVehicle_Id(1L, 2L)).thenReturn(false);
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> {
            Bid bid = invocation.getArgument(0);
            bid.setId(2L);
            return bid;
        });

        // When - fleet owner bids with fleet vehicle (should work)
        Bid result = bidService.createBid(
                1L, fleetOwner, 2L, new BigDecimal("1200.00"), "Fleet bid", null
        );

        // Then
        assertThat(result).isNotNull();
        verify(bidRepository).save(any(Bid.class));
    }

    @Test
    @DisplayName("Should prevent duplicate bids")
    void testCreateBid_DuplicateBid() {
        // Given
        when(freightRepository.findById(1L)).thenReturn(Optional.of(freight));
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(driverVehicle));
        when(bidRepository.existsByCargoRequest_IdAndVehicle_Id(1L, 1L)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> 
            bidService.createBid(1L, driver, 1L, new BigDecimal("1000.00"), "Duplicate", null)
        ).isInstanceOf(BidValidationException.class)
         .hasMessageContaining("has already bid on freight");
    }

    @Test
    @DisplayName("Should require available datetime for intracity")
    void testCreateBid_IntracityRequiresDateTime() {
        // Given
        freight.setServiceType(Freight.ServiceType.INTRACITY);
        when(freightRepository.findById(1L)).thenReturn(Optional.of(freight));
        when(vehicleRepository.findById(1L)).thenReturn(Optional.of(driverVehicle));
        when(bidRepository.existsByCargoRequest_IdAndVehicle_Id(1L, 1L)).thenReturn(false);

        // When & Then - no available datetime
        assertThatThrownBy(() -> 
            bidService.createBid(1L, driver, 1L, new BigDecimal("1000.00"), "Intracity", null)
        ).isInstanceOf(BidValidationException.class)
         .hasMessageContaining("available_datetime is required");

        // When & Then - past datetime
        assertThatThrownBy(() -> 
            bidService.createBid(1L, driver, 1L, new BigDecimal("1000.00"), 
                               "Intracity", LocalDateTime.now().minusHours(1))
        ).isInstanceOf(BidValidationException.class)
         .hasMessageContaining("must be in the future");
    }

    @Test
    @DisplayName("Should authorize bid viewing correctly")
    void testGetBidWithAuthorization() {
        // Given
        Bid bid = Bid.builder()
                .id(1L)
                .driver(driver)
                .cargoRequest(freight)
                .build();

        when(bidRepository.findById(1L)).thenReturn(Optional.of(bid));

        // When & Then - driver can view own bid
        assertThat(bidService.getBidWithAuthorization(1L, driver)).isEqualTo(bid);

        // When & Then - shipper can view bid on their freight
        assertThat(bidService.getBidWithAuthorization(1L, shipper)).isEqualTo(bid);

        // When & Then - admin can view any bid
        assertThat(bidService.getBidWithAuthorization(1L, admin)).isEqualTo(bid);

        // When & Then - unauthorized user cannot view bid
        User otherUser = User.builder().id(999L).role(User.Role.DRIVER).build();
        assertThatThrownBy(() -> 
            bidService.getBidWithAuthorization(1L, otherUser)
        ).isInstanceOf(AuthorizationException.class);
    }

    @Test
    @DisplayName("Should validate counter-offer authorization")
    void testCreateCounterOffer_Authorization() {
        // Given
        Bid bid = Bid.builder()
                .id(1L)
                .driver(driver)
                .cargoRequest(freight)
                .status(Bid.BidStatus.PENDING)
                .build();

        // When & Then - shipper can counter pending bid
        when(bidRepository.save(any(Bid.class))).thenReturn(bid);
        Bid result = bidService.createCounterOffer(bid, shipper, new BigDecimal("950.00"), "Counter");
        assertThat(result.getCounterBy()).isEqualTo(Bid.CounterBy.SHIPPER);

        // When & Then - unauthorized user cannot counter
        User otherUser = User.builder().id(999L).role(User.Role.DRIVER).build();
        assertThatThrownBy(() -> 
            bidService.createCounterOffer(bid, otherUser, new BigDecimal("900.00"), "Unauthorized")
        ).isInstanceOf(AuthorizationException.class);
    }

    @Test
    @DisplayName("Should validate counter-offer status requirements")
    void testCreateCounterOffer_StatusValidation() {
        // Given - accepted bid
        Bid acceptedBid = Bid.builder()
                .id(1L)
                .driver(driver)
                .cargoRequest(freight)
                .status(Bid.BidStatus.ACCEPTED)
                .build();

        // When & Then - cannot counter accepted bid
        assertThatThrownBy(() -> 
            bidService.createCounterOffer(acceptedBid, shipper, new BigDecimal("950.00"), "Too late")
        ).isInstanceOf(BidStatusException.class)
         .hasMessageContaining("Cannot counter");
    }

    @Test
    @DisplayName("Should validate withdrawal authorization")
    void testWithdrawBid_Authorization() {
        // Given
        Bid bid = Bid.builder()
                .id(1L)
                .driver(driver)
                .status(Bid.BidStatus.PENDING)
                .build();

        when(bidRepository.save(any(Bid.class))).thenReturn(bid);

        // When & Then - driver can withdraw own bid
        Bid result = bidService.withdrawBid(bid, driver);
        assertThat(result.getStatus()).isEqualTo(Bid.BidStatus.REJECTED);

        // When & Then - other user cannot withdraw
        User otherUser = User.builder().id(999L).role(User.Role.DRIVER).build();
        assertThatThrownBy(() -> 
            bidService.withdrawBid(bid, otherUser)
        ).isInstanceOf(AuthorizationException.class);
    }

    @Test
    @DisplayName("Should rank bids correctly")
    void testRankBids() {
        // Given
        Bid highBid = Bid.builder()
                .id(1L)
                .amount(new BigDecimal("1000.00"))
                .cargoRequest(freight)
                .driver(User.builder().id(1L).build())
                .build();

        Bid lowBid = Bid.builder()
                .id(2L)
                .amount(new BigDecimal("900.00"))
                .cargoRequest(freight)
                .driver(User.builder().id(2L).build())
                .build();

        // When
        List<Bid> ranked = bidService.rankBids(List.of(highBid, lowBid));

        // Then - should be sorted by amount ASC (lower first)
        assertThat(ranked).hasSize(2);
        assertThat(ranked.get(0)).isEqualTo(lowBid);
        assertThat(ranked.get(1)).isEqualTo(highBid);
        assertThat(ranked.get(0).getIsRecommended()).isTrue();
        assertThat(ranked.get(1).getIsRecommended()).isFalse();
    }
}