package com.ethioloadai.bid;

import com.ethioloadai.bid.entity.Bid;
import com.ethioloadai.bid.exception.BidStatusException;
import com.ethioloadai.bid.exception.BidValidationException;
import com.ethioloadai.bid.repository.BidRepository;
import com.ethioloadai.bid.service.BidService;
import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.service.BookingService;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.message.service.MessageService;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BidLifecycleTest {

    @Autowired
    private BidService bidService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private FreightRepository freightRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private MessageService messageService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User shipper;
    private User driver1;
    private User driver2;
    private User fleetOwner;
    private Freight freight;
    private Vehicle vehicle1;
    private Vehicle vehicle2;
    private Vehicle fleetVehicle;

    @BeforeEach
    void setUp() {
        // Create test users
        shipper = createUser("John Shipper", "1234567890", User.Role.SHIPPER);
        driver1 = createUser("Alice Driver", "1234567891", User.Role.DRIVER);
        driver2 = createUser("Bob Driver", "1234567892", User.Role.DRIVER);
        fleetOwner = createUser("Fleet Owner", "1234567893", User.Role.FLEET_OWNER);

        // Create test vehicles
        vehicle1 = createVehicle("ABC-001", driver1.getId(), null);
        vehicle2 = createVehicle("ABC-002", driver2.getId(), null);
        fleetVehicle = createVehicle("FLEET-001", fleetOwner.getId(), fleetOwner.getId());

        // Create test freight
        freight = createFreight(shipper);
    }

    @Test
    @DisplayName("Should create bid successfully with valid data")
    void testCreateBid_Success() {
        // When
        Bid bid = bidService.createBid(
                freight.getId(),
                driver1,
                vehicle1.getId(),
                new BigDecimal("1000.00"),
                "I can do this job",
                null
        );

        // Then
        assertThat(bid).isNotNull();
        assertThat(bid.getId()).isNotNull();
        assertThat(bid.getAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(bid.getStatus()).isEqualTo(Bid.BidStatus.PENDING);
        assertThat(bid.getDriver()).isEqualTo(driver1);
        assertThat(bid.getVehicle()).isEqualTo(vehicle1);
        assertThat(bid.getCargoRequest()).isEqualTo(freight);
    }

    @Test
    @DisplayName("Should prevent duplicate bids on same freight by same vehicle")
    void testCreateBid_DuplicateBid() {
        // Given - first bid exists
        bidService.createBid(freight.getId(), driver1, vehicle1.getId(), 
                            new BigDecimal("1000.00"), "First bid", null);

        // When & Then
        assertThatThrownBy(() -> 
            bidService.createBid(freight.getId(), driver1, vehicle1.getId(),
                               new BigDecimal("900.00"), "Second bid", null)
        ).isInstanceOf(BidValidationException.class)
         .hasMessageContaining("already bid on this freight");
    }

    @Test
    @DisplayName("Accepting a bid persists matched driver and grants messaging access")
    void acceptBidPersistsMatchedDriverAndAllowsMessaging() {
        Bid bid = bidService.createBid(freight.getId(), driver1, vehicle1.getId(),
                new BigDecimal("1000.00"), "Accepted assignment", null);

        Booking booking = bookingService.acceptBid(bid.getId(), shipper);

        assertThat(booking.getBookingStatus()).isEqualTo(Booking.BookingStatus.ACCEPTED);
        assertThat(booking.getDriver().getId()).isEqualTo(driver1.getId());
        assertThat(booking.getCargoRequest().getStatus()).isEqualTo(Freight.FreightStatus.MATCHED);

        entityManager.flush();
        entityManager.clear();
        Freight persistedFreight = freightRepository.findById(freight.getId()).orElseThrow();
        assertThat(persistedFreight.getMatchedDriver().getId()).isEqualTo(driver1.getId());

        assertThat(messageService.getFreightMessages(freight.getId(), driver1).messages()).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Failed bid acceptance rolls back booking, bid, freight status, and matched driver")
    void failedBidAcceptanceRollsBackAssignmentState() {
        Bid bid = bidService.createBid(freight.getId(), driver1, vehicle1.getId(),
                new BigDecimal("1000.00"), "Acceptance rollback", null);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

        try {
            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                bookingService.acceptBid(bid.getId(), shipper);
                throw new IllegalStateException("force acceptance transaction rollback");
            })).isInstanceOf(IllegalStateException.class)
                    .hasMessage("force acceptance transaction rollback");

            assertThat(bidRepository.findById(bid.getId()).orElseThrow().getStatus())
                    .isEqualTo(Bid.BidStatus.PENDING);
            assertThat(bookingService.hasActiveBooking(freight.getId())).isFalse();
            Freight persistedFreight = freightRepository.findById(freight.getId()).orElseThrow();
            assertThat(persistedFreight.getStatus()).isEqualTo(Freight.FreightStatus.POSTED);
            assertThat(persistedFreight.getMatchedDriver()).isNull();
        } finally {
            bidRepository.deleteById(bid.getId());
            freightRepository.deleteById(freight.getId());
            vehicleRepository.deleteAll(List.of(vehicle1, vehicle2, fleetVehicle));
            userRepository.deleteAll(List.of(shipper, driver1, driver2, fleetOwner));
        }
    }

    @Test
    @DisplayName("Should validate vehicle ownership for drivers")
    void testCreateBid_InvalidVehicleOwnership() {
        // When & Then - driver1 tries to bid with driver2's vehicle
        assertThatThrownBy(() -> 
            bidService.createBid(freight.getId(), driver1, vehicle2.getId(),
                               new BigDecimal("1000.00"), "Invalid ownership", null)
        ).isInstanceOf(BidValidationException.class)
         .hasMessageContaining("not owned by the driver");
    }

    @Test
    @DisplayName("Should validate fleet vehicle ownership")
    void testCreateBid_FleetVehicleOwnership() {
        // When - fleet owner bids with fleet vehicle (should work)
        Bid bid = bidService.createBid(
                freight.getId(),
                fleetOwner,
                fleetVehicle.getId(),
                new BigDecimal("1200.00"),
                "Fleet bid",
                null
        );

        // Then
        assertThat(bid).isNotNull();
        assertThat(bid.getDriver()).isEqualTo(fleetOwner);

        // When & Then - driver tries to bid with fleet vehicle (should fail)
        assertThatThrownBy(() -> 
            bidService.createBid(freight.getId(), driver1, fleetVehicle.getId(),
                               new BigDecimal("1000.00"), "Invalid fleet bid", null)
        ).isInstanceOf(BidValidationException.class)
         .hasMessageContaining("not owned by the driver");
    }

    @Test
    @DisplayName("Should require available datetime for intracity freight")
    void testCreateBid_IntracityRequiresDateTime() {
        // Given - intracity freight
        freight.setServiceType(Freight.ServiceType.INTRACITY);
        freightRepository.save(freight);

        // When & Then - bid without available datetime should fail
        assertThatThrownBy(() -> 
            bidService.createBid(freight.getId(), driver1, vehicle1.getId(),
                               new BigDecimal("1000.00"), "Intracity bid", null)
        ).isInstanceOf(BidValidationException.class)
         .hasMessageContaining("available_datetime is required");

        // When - bid with future datetime should succeed
        LocalDateTime futureTime = LocalDateTime.now().plusHours(2);
        Bid bid = bidService.createBid(
                freight.getId(),
                driver1,
                vehicle1.getId(),
                new BigDecimal("1000.00"),
                "Intracity bid",
                futureTime
        );

        // Then
        assertThat(bid.getAvailableDatetime()).isEqualTo(futureTime);
    }

    @Test
    @DisplayName("Should create and accept counter-offer successfully")
    void testCounterOffer_Success() {
        // Given - initial bid
        Bid bid = bidService.createBid(freight.getId(), driver1, vehicle1.getId(),
                                     new BigDecimal("1000.00"), "Initial bid", null);

        // When - shipper creates counter-offer
        Bid counteredBid = bidService.createCounterOffer(
                bid, shipper, new BigDecimal("950.00"), "How about 950?"
        );

        // Then
        assertThat(counteredBid.getStatus()).isEqualTo(Bid.BidStatus.COUNTERED);
        assertThat(counteredBid.getCounterAmount()).isEqualByComparingTo(new BigDecimal("950.00"));
        assertThat(counteredBid.getCounterBy()).isEqualTo(Bid.CounterBy.SHIPPER);
        assertThat(counteredBid.getCounterNote()).isEqualTo("How about 950?");

        // When - driver accepts counter-offer
        Booking booking = bookingService.acceptCounterOffer(counteredBid.getId(), driver1);

        // Then
        assertThat(booking).isNotNull();
        assertThat(booking.getEstimatedPrice()).isEqualByComparingTo(new BigDecimal("950.00"));
        assertThat(booking.getCargoRequest().getMatchedDriver().getId()).isEqualTo(driver1.getId());
        
        // Refresh bid from database
        Bid finalBid = bidRepository.findById(bid.getId()).orElseThrow();
        assertThat(finalBid.getStatus()).isEqualTo(Bid.BidStatus.ACCEPTED);
    }

    @Test
    @DisplayName("Should withdraw bid successfully")
    void testWithdrawBid_Success() {
        // Given
        Bid bid = bidService.createBid(freight.getId(), driver1, vehicle1.getId(),
                                     new BigDecimal("1000.00"), "Will withdraw", null);

        // When
        Bid withdrawnBid = bidService.withdrawBid(bid, driver1);

        // Then
        assertThat(withdrawnBid.getStatus()).isEqualTo(Bid.BidStatus.REJECTED);
    }

    @Test
    @DisplayName("Should reject bid successfully")
    void testRejectBid_Success() {
        // Given
        Bid bid = bidService.createBid(freight.getId(), driver1, vehicle1.getId(),
                                     new BigDecimal("1000.00"), "Will be rejected", null);

        // When
        Bid rejectedBid = bidService.rejectBid(bid, shipper);

        // Then
        assertThat(rejectedBid.getStatus()).isEqualTo(Bid.BidStatus.REJECTED);
    }

    @Test
    @DisplayName("Should prevent actions on final bids")
    void testFinalBidActions() {
        // Given - accepted bid
        Bid bid = bidService.createBid(freight.getId(), driver1, vehicle1.getId(),
                                     new BigDecimal("1000.00"), "Final bid", null);
        bookingService.acceptBid(bid.getId(), shipper);

        // Refresh bid
        Bid acceptedBid = bidRepository.findById(bid.getId()).orElseThrow();

        // When & Then - cannot withdraw accepted bid
        assertThatThrownBy(() -> bidService.withdrawBid(acceptedBid, driver1))
                .isInstanceOf(BidStatusException.class)
                .hasMessageContaining("already in final state");

        // When & Then - cannot reject accepted bid
        assertThatThrownBy(() -> bidService.rejectBid(acceptedBid, shipper))
                .isInstanceOf(BidStatusException.class)
                .hasMessageContaining("already in final state");

        // When & Then - cannot create counter-offer on accepted bid
        assertThatThrownBy(() -> 
            bidService.createCounterOffer(acceptedBid, shipper, new BigDecimal("900.00"), "Too late")
        ).isInstanceOf(BidStatusException.class)
         .hasMessageContaining("not eligible");
    }

    @Test
    @DisplayName("Should rank bids correctly")
    void testBidRanking() {
        // Given - multiple bids with different amounts
        Bid bid1 = bidService.createBid(freight.getId(), driver1, vehicle1.getId(),
                                      new BigDecimal("1000.00"), "Higher bid", null);
        Bid bid2 = bidService.createBid(freight.getId(), driver2, vehicle2.getId(),
                                      new BigDecimal("900.00"), "Lower bid", null);

        List<Bid> bids = List.of(bid1, bid2);

        // When
        List<Bid> rankedBids = bidService.rankBids(bids);

        // Then - should be sorted by amount ASC (lower first)
        assertThat(rankedBids).hasSize(2);
        assertThat(rankedBids.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("900.00"));
        assertThat(rankedBids.get(1).getAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
        
        // Top bid should be marked as recommended
        assertThat(rankedBids.get(0).getIsRecommended()).isTrue();
        assertThat(rankedBids.get(1).getIsRecommended()).isFalse();
    }

    // Helper methods

    private User createUser(String fullName, String phone, User.Role role) {
        User user = User.builder()
                .fullName(fullName)
                .phone(phone)
                .email(phone + "@test.com")
                .password("password")
                .role(role)
                .verificationStatus(true)
                .isActive(true)
                .build();
        return userRepository.save(user);
    }

    private Vehicle createVehicle(String plateNumber, Long userId, Long fleetOwnerId) {
        Vehicle vehicle = Vehicle.builder()
                .plateNumber(plateNumber)
                .truckType("Isuzu NPR")
                .vehicleCategory(Vehicle.VehicleCategory.LIGHT)
                .capacity(3.0)
                .userId(userId)
                .fleetOwnerId(fleetOwnerId)
                .currentCity("Addis Ababa")
                .availabilityStatus("available")
                .approvalStatus(Vehicle.VehicleApprovalStatus.APPROVED)
                .build();
        return vehicleRepository.save(vehicle);
    }

    private Freight createFreight(User shipper) {
        Freight freight = Freight.builder()
                .shipper(shipper)
                .pickupLocation("Merkato")
                .destination("Bole")
                .materialType("general")
                .cargoDescription("Test cargo")
                .serviceType(Freight.ServiceType.INTERCITY)
                .priceType(Freight.PriceType.NEGOTIABLE)
                .budget(new BigDecimal("1500.00"))
                .weightTons(new BigDecimal("5.00"))
                .urgencyLevel("normal")
                .status(Freight.FreightStatus.POSTED)
                .build();
        return freightRepository.save(freight);
    }
}