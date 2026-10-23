package com.ethioloadai.carrier.dispatch;

import com.ethioloadai.bid.entity.Bid;
import com.ethioloadai.bid.repository.BidRepository;
import com.ethioloadai.bid.service.BidService;
import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.booking.service.BookingService;
import com.ethioloadai.carrier.dispatch.dto.CreateDispatchRequest;
import com.ethioloadai.carrier.dispatch.entity.DispatchAssignment;
import com.ethioloadai.carrier.dispatch.entity.DispatchStatus;
import com.ethioloadai.carrier.dispatch.service.DispatchService;
import com.ethioloadai.carrier.offer.dto.CounterOfferRequest;
import com.ethioloadai.carrier.offer.dto.SubmitOfferRequest;
import com.ethioloadai.carrier.offer.entity.CarrierOffer;
import com.ethioloadai.carrier.offer.repository.CarrierOfferRepository;
import com.ethioloadai.freight.dto.UpdateFreightRequest;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.entity.Vehicle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "ethioloadai.postgres-concurrency", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FreightCancellationPostgresConcurrencyTest extends DispatchTestSupport {

    @Autowired BidService bidService;
    @Autowired BidRepository bidRepository;
    @Autowired BookingService bookingService;
    @Autowired DispatchService dispatchService;
    @Autowired CarrierOfferRepository carrierOfferRepository;
    @Autowired BookingRepository bookingRepository;

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> requiredEnvironment("ETHIOLOADAI_POSTGRES_TEST_URL"));
        registry.add("spring.datasource.username",
                () -> requiredEnvironment("ETHIOLOADAI_POSTGRES_TEST_USERNAME"));
        registry.add("spring.datasource.password",
                () -> requiredEnvironment("ETHIOLOADAI_POSTGRES_TEST_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Test
    void cancellationRacesBidCreation() throws Exception {
        Freight freight = mkFreight(shipper, Freight.PriceType.NEGOTIABLE,
                new BigDecimal("50000.00"));
        User bidder = mkUser(User.Role.DRIVER);
        Vehicle vehicle = mkDriverVehicle(bidder);

        List<Outcome> outcomes = race(
                () -> {
                    freightService.cancelFreight(freight.getId(), shipper.getId());
                    return null;
                },
                () -> bidService.createBid(freight.getId(), bidder, vehicle.getId(),
                        new BigDecimal("41000.00"), "concurrent bid", null));

        Freight result = freightRepo.findById(freight.getId()).orElseThrow();
        boolean hasActionableBid = bidRepository.existsByCargoRequest_IdAndStatusIn(
                freight.getId(), List.of(Bid.BidStatus.PENDING, Bid.BidStatus.COUNTERED));
        if (result.getStatus() == Freight.FreightStatus.CANCELLED) {
            assertThat(hasActionableBid).isFalse();
            assertThat(outcomes.get(1).failure()).isNotNull();
        } else {
            assertThat(result.getStatus()).isEqualTo(Freight.FreightStatus.POSTED);
            assertThat(hasActionableBid).isTrue();
            assertThat(outcomes.get(1).failure()).isNull();
            assertThat(outcomes.get(0).failure()).isNotNull();
        }
    }

    @Test
    void patchCancellationRacesBidCreation() throws Exception {
        Freight freight = mkFreight(shipper, Freight.PriceType.NEGOTIABLE,
                new BigDecimal("50000.00"));
        User bidder = mkUser(User.Role.DRIVER);
        Vehicle vehicle = mkDriverVehicle(bidder);
        UpdateFreightRequest cancellation = UpdateFreightRequest.builder()
                .status(Freight.FreightStatus.CANCELLED)
                .build();

        List<Outcome> outcomes = race(
                () -> freightService.updateFreight(freight.getId(), shipper.getId(), cancellation),
                () -> bidService.createBid(freight.getId(), bidder, vehicle.getId(),
                        new BigDecimal("41000.00"), "concurrent bid against PATCH cancellation", null));

        Freight result = freightRepo.findById(freight.getId()).orElseThrow();
        boolean hasActionableBid = bidRepository.existsByCargoRequest_IdAndStatusIn(
                freight.getId(), List.of(Bid.BidStatus.PENDING, Bid.BidStatus.COUNTERED));
        if (result.getStatus() == Freight.FreightStatus.CANCELLED) {
            assertThat(hasActionableBid).isFalse();
            assertThat(outcomes.get(1).failure()).isNotNull();
        } else {
            assertThat(result.getStatus()).isEqualTo(Freight.FreightStatus.POSTED);
            assertThat(hasActionableBid).isTrue();
            assertThat(outcomes.get(1).failure()).isNull();
            assertThat(outcomes.get(0).failure()).isNotNull();
        }
    }

    @Test
    void cancellationRacesCarrierOfferCreation() throws Exception {
        Freight freight = mkFreight(shipper, Freight.PriceType.NEGOTIABLE,
                new BigDecimal("50000.00"));

        List<Outcome> outcomes = race(
                () -> {
                    freightService.cancelFreight(freight.getId(), shipper.getId());
                    return null;
                },
                () -> offerService.submitOffer(freight.getId(), abcCompanyId, abcOwner,
                        new SubmitOfferRequest(new BigDecimal("42000.00"), abcDriverA.getId(),
                                abcVehicle1Id, "concurrent offer")));

        Freight result = freightRepo.findById(freight.getId()).orElseThrow();
        boolean actionableOffer = carrierOfferRepository.existsByFreight_IdAndStatusIn(
                freight.getId(), List.of(CarrierOffer.OfferStatus.PENDING,
                        CarrierOffer.OfferStatus.NEGOTIATING));
        if (result.getStatus() == Freight.FreightStatus.CANCELLED) {
            assertThat(actionableOffer).isFalse();
            assertThat(outcomes.get(1).failure()).isNotNull();
        } else {
            assertThat(result.getStatus()).isEqualTo(Freight.FreightStatus.POSTED);
            assertThat(actionableOffer).isTrue();
            assertThat(outcomes.get(1).failure()).isNull();
            assertThat(outcomes.get(0).failure()).isNotNull();
        }
    }

    @Test
    void cancellationRacesBidAcceptance() throws Exception {
        Freight freight = mkFreight(shipper, Freight.PriceType.NEGOTIABLE,
                new BigDecimal("50000.00"));
        User bidder = mkUser(User.Role.DRIVER);
        Vehicle vehicle = mkDriverVehicle(bidder);
        Bid bid = bidService.createBid(freight.getId(), bidder, vehicle.getId(),
                new BigDecimal("41000.00"), "acceptance race", null);

        List<Outcome> outcomes = race(
                () -> {
                    freightService.cancelFreight(freight.getId(), shipper.getId());
                    return null;
                },
                () -> bookingService.acceptBid(bid.getId(), shipper));

        Freight result = freightRepo.findById(freight.getId()).orElseThrow();
        assertThat(result.getStatus()).isNotEqualTo(Freight.FreightStatus.CANCELLED);
        assertThat(outcomes.get(0).failure()).isNotNull();
        assertThat(outcomes.get(1).failure()).isNull();
        assertThat(result.getStatus()).isEqualTo(Freight.FreightStatus.MATCHED);
        assertThat(bookingRepository.existsByCargoRequest_IdAndBookingStatusIn(
                freight.getId(), List.of(Booking.BookingStatus.ACCEPTED))).isTrue();
    }

    @Test
    void cancellationRacesCarrierOfferAcceptance() throws Exception {
        Freight freight = mkFreight(shipper, Freight.PriceType.NEGOTIABLE,
                new BigDecimal("50000.00"));
        var offer = offerService.submitOffer(freight.getId(), abcCompanyId, abcOwner,
                new SubmitOfferRequest(new BigDecimal("42000.00"), abcDriverA.getId(),
                        abcVehicle1Id, "acceptance race"));
        offerService.shipperCounter(freight.getId(), offer.id(), shipper,
                new CounterOfferRequest(new BigDecimal("43000.00"), "counter"));

        List<Outcome> outcomes = race(
                () -> {
                    freightService.cancelFreight(freight.getId(), shipper.getId());
                    return null;
                },
                () -> offerService.carrierAccept(offer.id(), abcCompanyId, abcOwner));

        Freight result = freightRepo.findById(freight.getId()).orElseThrow();
        CarrierOffer persistedOffer = carrierOfferRepository.findById(offer.id()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(Freight.FreightStatus.POSTED);
        assertThat(result.getCarrierAwardOfferId()).isEqualTo(offer.id());
        assertThat(persistedOffer.getStatus()).isEqualTo(CarrierOffer.OfferStatus.ACCEPTED);
        assertThat(outcomes.get(0).failure()).isNotNull();
        assertThat(outcomes.get(1).failure()).isNull();
    }

    @Test
    void cancellationRacesDispatchCreation() throws Exception {
        CreateDispatchRequest request = new CreateDispatchRequest();
        request.setDriverUserId(abcDriverA.getId());
        request.setVehicleId(abcVehicle1Id);

        List<Outcome> outcomes = race(
                () -> {
                    freightService.cancelFreight(awardedFreight.getId(), shipper.getId());
                    return null;
                },
                () -> dispatchService.createDispatch(
                        awardedFreight.getId(), abcCompanyId, request, abcOwner));

        Freight result = freightRepo.findById(awardedFreight.getId()).orElseThrow();
        List<DispatchAssignment> assignments =
                dispatchRepo.findByFreightIdAndCarrierCompanyIdOrderByAssignedAtDesc(
                        awardedFreight.getId(), abcCompanyId);
        assertThat(result.getStatus()).isEqualTo(Freight.FreightStatus.POSTED);
        assertThat(outcomes.get(0).failure()).isNotNull();
        assertThat(outcomes.get(1).failure()).isNull();
        assertThat(assignments).filteredOn(d -> d.getStatus() == DispatchStatus.ASSIGNED).hasSize(1);
    }

    @Test
    void cancellationRacesDispatchAcceptance() throws Exception {
        CreateDispatchRequest request = new CreateDispatchRequest();
        request.setDriverUserId(abcDriverA.getId());
        request.setVehicleId(abcVehicle1Id);
        var assignment = dispatchService.createDispatch(
                awardedFreight.getId(), abcCompanyId, request, abcOwner);

        List<Outcome> outcomes = race(
                () -> {
                    freightService.cancelFreight(awardedFreight.getId(), shipper.getId());
                    return null;
                },
                () -> dispatchService.acceptAssignment(assignment.getId(), abcDriverA));

        Freight result = freightRepo.findById(awardedFreight.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(Freight.FreightStatus.MATCHED);
        assertThat(outcomes.get(0).failure()).isNotNull();
        assertThat(outcomes.get(1).failure()).isNull();
        assertThat(bookingRepository.existsByCargoRequest_IdAndBookingStatusIn(
                awardedFreight.getId(), List.of(Booking.BookingStatus.ACCEPTED))).isTrue();
        assertThat(dispatchRepo.findById(assignment.getId()).orElseThrow().getStatus())
                .isEqualTo(DispatchStatus.DRIVER_ACCEPTED);
    }

    private Vehicle mkDriverVehicle(User driver) {
        return vehicleRepo.saveAndFlush(Vehicle.builder()
                .plateNumber("PG-" + UUID.randomUUID().toString().substring(0, 8))
                .truckType("Concurrency Truck")
                .vehicleCategory(Vehicle.VehicleCategory.LIGHT)
                .capacity(20.0)
                .userId(driver.getId())
                .currentCity("Addis Ababa")
                .availabilityStatus("available")
                .approvalStatus(Vehicle.VehicleApprovalStatus.APPROVED)
                .build());
    }

    private List<Outcome> race(Callable<?> left, Callable<?> right) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Outcome> first = executor.submit(runAfterBarrier(left, ready, start));
            Future<Outcome> second = executor.submit(runAfterBarrier(right, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(60, TimeUnit.SECONDS), second.get(60, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Callable<Outcome> runAfterBarrier(
            Callable<?> operation, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent test start");
            }
            try {
                return new Outcome(operation.call(), null);
            } catch (RuntimeException failure) {
                return new Outcome(null, failure);
            }
        };
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required environment variable is not set: " + name);
        }
        return value;
    }

    private record Outcome(Object value, RuntimeException failure) {}
}
