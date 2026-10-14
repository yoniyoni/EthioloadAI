package com.ethioloadai.bid;

import com.ethioloadai.bid.entity.Bid;
import com.ethioloadai.bid.repository.BidRepository;
import com.ethioloadai.bid.service.BidService;
import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.booking.service.BookingService;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BidConcurrencyTest {

    @Autowired
    private BidService bidService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private FreightRepository freightRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    private User shipper;
    private User driver1;
    private User driver2;
    private User driver3;
    private Freight freight;
    private Vehicle vehicle1;
    private Vehicle vehicle2;
    private Vehicle vehicle3;

    @BeforeEach
    void setUp() {
        // Create test users
        shipper = createUser("Concurrent Shipper", "9876543210", User.Role.SHIPPER);
        driver1 = createUser("Driver One", "9876543211", User.Role.DRIVER);
        driver2 = createUser("Driver Two", "9876543212", User.Role.DRIVER);
        driver3 = createUser("Driver Three", "9876543213", User.Role.DRIVER);

        // Create test vehicles
        vehicle1 = createVehicle("CONC-001", driver1.getId(), null);
        vehicle2 = createVehicle("CONC-002", driver2.getId(), null);
        vehicle3 = createVehicle("CONC-003", driver3.getId(), null);

        // Create test freight
        freight = createFreight(shipper);
    }

    @Test
    @DisplayName("CRITICAL: Should handle concurrent bid acceptance safely - only one succeeds")
    void testConcurrentBidAcceptance() throws Exception {
        // Given - create multiple competing bids
        Bid bid1 = bidService.createBid(freight.getId(), driver1, vehicle1.getId(),
                                      new BigDecimal("1000.00"), "First bid", null);
        Bid bid2 = bidService.createBid(freight.getId(), driver2, vehicle2.getId(),
                                      new BigDecimal("950.00"), "Second bid", null);
        Bid bid3 = bidService.createBid(freight.getId(), driver3, vehicle3.getId(),
                                      new BigDecimal("1100.00"), "Third bid", null);

        // Create thread pool for concurrent execution
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(3);

        // Results tracking
        ConcurrentLinkedQueue<CompletionResult> results = new ConcurrentLinkedQueue<>();

        // Create concurrent acceptance tasks
        Callable<CompletionResult> acceptTask1 = createAcceptanceTask(bid1.getId(), shipper, startLatch, completeLatch, results, "Task1");
        Callable<CompletionResult> acceptTask2 = createAcceptanceTask(bid2.getId(), shipper, startLatch, completeLatch, results, "Task2");  
        Callable<CompletionResult> acceptTask3 = createAcceptanceTask(bid3.getId(), shipper, startLatch, completeLatch, results, "Task3");

        try {
                // Submit all tasks without waiting so the start latch can be released.
                List<Future<CompletionResult>> futures = List.of(
                    executor.submit(acceptTask1),
                    executor.submit(acceptTask2),
                    executor.submit(acceptTask3));

            // Start all tasks simultaneously
            startLatch.countDown();

            // Wait for all tasks to complete (with timeout)
            boolean completed = completeLatch.await(30, TimeUnit.SECONDS);
            assertThat(completed).isTrue();

            // Wait for futures to complete
            for (Future<CompletionResult> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }

            // Analyze results
            analyzeResults(results, bid1.getId(), bid2.getId(), bid3.getId());

        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("Should handle concurrent counter-offer acceptance safely")
    void testConcurrentCounterOfferAcceptance() throws Exception {
        // Given - create bid with counter-offer
        Bid bid = bidService.createBid(freight.getId(), driver1, vehicle1.getId(),
                                     new BigDecimal("1000.00"), "Counter bid", null);
        
        Bid counteredBid = bidService.createCounterOffer(
                bid, shipper, new BigDecimal("950.00"), "Counter-offer"
        );

        // Create competing acceptance attempts
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(2);
        ConcurrentLinkedQueue<CompletionResult> results = new ConcurrentLinkedQueue<>();

        // Both shipper and driver try to accept simultaneously (should not happen in real world, but test race condition)
        Callable<CompletionResult> shipperTask = createCounterAcceptanceTask(counteredBid.getId(), shipper, startLatch, completeLatch, results, "Shipper");
        Callable<CompletionResult> driverTask = createCounterAcceptanceTask(counteredBid.getId(), driver1, startLatch, completeLatch, results, "Driver");

        try {
                List<Future<CompletionResult>> futures = List.of(
                    executor.submit(shipperTask),
                    executor.submit(driverTask));

            startLatch.countDown();
            boolean completed = completeLatch.await(30, TimeUnit.SECONDS);
            assertThat(completed).isTrue();

            for (Future<CompletionResult> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }

            // Should have exactly one success and one failure
            List<CompletionResult> successResults = results.stream()
                    .filter(r -> r.success)
                    .toList();
            List<CompletionResult> failureResults = results.stream()
                    .filter(r -> !r.success)
                    .toList();

            assertThat(successResults).hasSize(1);
            assertThat(failureResults).hasSize(1);

            // Verify only one booking was created
            List<Booking> bookings = bookingRepository.findByCargoRequest_Id(freight.getId());
            assertThat(bookings).hasSize(1);

        } finally {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("Should prevent double booking under high concurrency")
    void testHighConcurrencyDoubleBookingPrevention() throws Exception {
        // Given - create many bids
        Bid[] bids = new Bid[10];
        for (int i = 0; i < 10; i++) {
            User driver = createUser("Driver" + i, "123456789" + i, User.Role.DRIVER);
            Vehicle vehicle = createVehicle("VEH-" + i, driver.getId(), null);
            bids[i] = bidService.createBid(freight.getId(), driver, vehicle.getId(),
                                         new BigDecimal("1000.0" + i), "Bid " + i, null);
        }

        // When - all bids try to be accepted simultaneously
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(10);
        ConcurrentLinkedQueue<CompletionResult> results = new ConcurrentLinkedQueue<>();

        List<Callable<CompletionResult>> tasks = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(createAcceptanceTask(bids[i].getId(), shipper, startLatch, completeLatch, results, "Task" + i));
        }

        try {
            List<Future<CompletionResult>> futures = new java.util.ArrayList<>();
            for (Callable<CompletionResult> task : tasks) {
                futures.add(executor.submit(task));
            }
            
            startLatch.countDown();
            boolean completed = completeLatch.await(60, TimeUnit.SECONDS);
            assertThat(completed).isTrue();

            for (Future<CompletionResult> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

            // Then - exactly one acceptance should succeed
            List<CompletionResult> successResults = results.stream()
                    .filter(r -> r.success)
                    .toList();

            assertThat(successResults).hasSize(1);

            // Verify database consistency
            List<Booking> bookings = bookingRepository.findByCargoRequest_Id(freight.getId());
            assertThat(bookings).hasSize(1);

            List<Bid> acceptedBids = bidRepository.findByCargoRequestIdWithDetails(freight.getId())
                    .stream()
                    .filter(b -> b.getStatus() == Bid.BidStatus.ACCEPTED)
                    .toList();
            assertThat(acceptedBids).hasSize(1);

            // Verify freight status updated correctly
            Freight updatedFreight = freightRepository.findById(freight.getId()).orElseThrow();
            assertThat(updatedFreight.getStatus()).isEqualTo(Freight.FreightStatus.MATCHED);

        } finally {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    // Helper methods and classes

    private static class CompletionResult {
        final String taskName;
        final boolean success;
        final String errorType;
        final String errorMessage;
        final Booking booking;

        CompletionResult(String taskName, boolean success, String errorType, String errorMessage, Booking booking) {
            this.taskName = taskName;
            this.success = success;
            this.errorType = errorType;
            this.errorMessage = errorMessage;
            this.booking = booking;
        }
    }

    private Callable<CompletionResult> createAcceptanceTask(Long bidId, User shipper, 
                                                          CountDownLatch startLatch, 
                                                          CountDownLatch completeLatch, 
                                                          ConcurrentLinkedQueue<CompletionResult> results,
                                                          String taskName) {
        return () -> {
            try {
                startLatch.await(); // Wait for all tasks to be ready
                
                Booking booking = bookingService.acceptBid(bidId, shipper);
                CompletionResult result = new CompletionResult(taskName, true, null, null, booking);
                results.add(result);
                return result;
                
            } catch (Exception e) {
                CompletionResult result = new CompletionResult(taskName, false, e.getClass().getSimpleName(), e.getMessage(), null);
                results.add(result);
                return result;
            } finally {
                completeLatch.countDown();
            }
        };
    }

    private Callable<CompletionResult> createCounterAcceptanceTask(Long bidId, User user,
                                                                 CountDownLatch startLatch,
                                                                 CountDownLatch completeLatch,
                                                                 ConcurrentLinkedQueue<CompletionResult> results,
                                                                 String taskName) {
        return () -> {
            try {
                startLatch.await();
                
                Booking booking = bookingService.acceptCounterOffer(bidId, user);
                CompletionResult result = new CompletionResult(taskName, true, null, null, booking);
                results.add(result);
                return result;
                
            } catch (Exception e) {
                CompletionResult result = new CompletionResult(taskName, false, e.getClass().getSimpleName(), e.getMessage(), null);
                results.add(result);
                return result;
            } finally {
                completeLatch.countDown();
            }
        };
    }

    private void analyzeResults(ConcurrentLinkedQueue<CompletionResult> results, Long bid1Id, Long bid2Id, Long bid3Id) {
        List<CompletionResult> successResults = results.stream()
                .filter(r -> r.success)
                .toList();
        List<CompletionResult> failureResults = results.stream()
                .filter(r -> !r.success)
                .toList();

        // CRITICAL ASSERTION: Exactly one bid acceptance should succeed
        assertThat(successResults).hasSize(1);
        assertThat(failureResults).hasSize(2);

        System.out.println("=== CONCURRENCY TEST RESULTS ===");
        System.out.println("Successful acceptances: " + successResults.size());
        System.out.println("Failed acceptances: " + failureResults.size());
        
        for (CompletionResult result : results) {
            if (result.success) {
                System.out.println("SUCCESS: " + result.taskName + " - Booking ID: " + result.booking.getId());
            } else {
                System.out.println("FAILURE: " + result.taskName + " - " + result.errorType + ": " + result.errorMessage);
            }
        }

        // Verify database consistency
        List<Booking> allBookings = bookingRepository.findByCargoRequest_Id(freight.getId());
        assertThat(allBookings)
                .hasSize(1)
                .describedAs("Should have exactly one booking created");

        List<Bid> acceptedBids = bidRepository.findByCargoRequestIdWithDetails(freight.getId())
                .stream()
                .filter(bid -> bid.getStatus() == Bid.BidStatus.ACCEPTED)
                .toList();
        assertThat(acceptedBids)
                .hasSize(1)
                .describedAs("Should have exactly one accepted bid");

        List<Bid> rejectedBids = bidRepository.findByCargoRequestIdWithDetails(freight.getId())
                .stream()
                .filter(bid -> bid.getStatus() == Bid.BidStatus.REJECTED)
                .toList();
        assertThat(rejectedBids)
                .hasSize(2)
                .describedAs("Should have exactly two rejected bids");

        System.out.println("=== DATABASE CONSISTENCY VERIFIED ===");
        System.out.println("Total bookings: " + allBookings.size());
        System.out.println("Accepted bids: " + acceptedBids.size());
        System.out.println("Rejected bids: " + rejectedBids.size());
    }

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
                .truckType("Concurrent Truck")
                .vehicleCategory(Vehicle.VehicleCategory.LIGHT)
                .capacity(2.0)
                .userId(userId)
                .fleetOwnerId(fleetOwnerId)
                .currentCity("Test City")
                .availabilityStatus("available")
                .approvalStatus(Vehicle.VehicleApprovalStatus.APPROVED)
                .build();
        return vehicleRepository.save(vehicle);
    }

    private Freight createFreight(User shipper) {
        Freight freight = Freight.builder()
                .shipper(shipper)
                .pickupLocation("Concurrent Pickup")
                .destination("Concurrent Destination")
                .materialType("general")
                .cargoDescription("Concurrency test cargo")
                .serviceType(Freight.ServiceType.INTERCITY)
                .priceType(Freight.PriceType.NEGOTIABLE)
                .budget(new BigDecimal("2000.00"))
                .weightTons(new BigDecimal("5.00"))
                .urgencyLevel("normal")
                .status(Freight.FreightStatus.POSTED)
                .build();
        return freightRepository.save(freight);
    }
}