package com.ethioloadai.bid;

import com.ethioloadai.bid.entity.Bid;
import com.ethioloadai.bid.repository.BidRepository;
import com.ethioloadai.bid.service.BidService;
import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.rating.entity.Rating;
import com.ethioloadai.rating.repository.RatingRepository;
import com.ethioloadai.security.jwt.JwtService;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.ethioloadai.bid.BidPaginationIntegrationTest$SqlCapture"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class BidPaginationIntegrationTest {
    @Autowired private BidRepository bidRepository;
    @Autowired private BidService bidService;
    @Autowired private FreightRepository freightRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private RatingRepository ratingRepository;
    @Autowired private jakarta.persistence.EntityManager entityManager;
    @Autowired private JwtService jwtService;
    @Autowired private MockMvc mvc;

    @BeforeEach
    void clearSql() {
        SqlCapture.SQL.clear();
    }

    @Test
    void intercityPagesFollowGlobalRatingAndIdOrderAndOnlyRecommendGlobalFirst() {
        User shipper = user(User.Role.SHIPPER);
        User higherRated = user(User.Role.DRIVER);
        User tiedRatingFirst = user(User.Role.DRIVER);
        User tiedRatingSecond = user(User.Role.DRIVER);
        User unrated = user(User.Role.DRIVER);
        Freight freight = freight(shipper, Freight.ServiceType.INTERCITY);

        Bid idTieFirst = bid(freight, tiedRatingFirst, "100.00", Bid.BidStatus.PENDING);
        Bid idTieSecond = bid(freight, tiedRatingSecond, "100.0", Bid.BidStatus.ACCEPTED);
        Bid ratingWinner = bid(freight, higherRated, "100", Bid.BidStatus.COUNTERED);
        Bid unratedBid = bid(freight, unrated, "100.00", Bid.BidStatus.REJECTED);
        Bid higherAmount = bid(freight, user(User.Role.DRIVER), "120", Bid.BidStatus.EXPIRED);
        rate(shipper, tiedRatingFirst, freight, 4);
        rate(shipper, tiedRatingSecond, freight, 4);
        rate(shipper, higherRated, freight, 5);
        entityManager.flush();
        entityManager.clear();
        SqlCapture.SQL.clear();

        BidService.FreightBidsPage firstPage =
                bidService.getBidsForFreightPage(freight.getId(), shipper, PageRequest.of(0, 2));
        assertThat(firstPage.bids().getContent()).extracting(Bid::getId)
                .containsExactly(ratingWinner.getId(), idTieFirst.getId());
        assertThat(firstPage.bids().hasNext()).isTrue();
        assertThat(firstPage.pendingCount()).isEqualTo(2);
        assertThat(SqlCapture.SQL).anySatisfy(sql -> {
            String normalized = sql.toLowerCase();
            assertThat(normalized).contains("order by", "driver_rating");
            assertThat(normalized).contains("fetch first");
            assertThat(normalized).contains("group by", "ratings");
        });
        assertThat(SqlCapture.SQL).anySatisfy(sql -> {
            String normalized = sql.toLowerCase();
            assertThat(normalized).contains("from bids", "join", " in (");
        });
        assertThat(SqlCapture.SQL.stream()
                .filter(sql -> sql.toLowerCase().contains("ratings")))
                .hasSize(1);
        assertThat(SqlCapture.SQL.stream()
                .filter(sql -> sql.toLowerCase().contains("fetch first")))
                .hasSize(1);
        assertThat(SqlCapture.SQL.stream()
                .filter(sql -> sql.toLowerCase().contains("join")
                        && sql.toLowerCase().contains("vehicle")
                        && sql.toLowerCase().contains(" in (")))
                .hasSize(1);

        SqlCapture.SQL.clear();
        BidService.FreightBidsPage secondPage =
                bidService.getBidsForFreightPage(freight.getId(), shipper, PageRequest.of(1, 2));
        assertThat(secondPage.bids().getContent()).extracting(Bid::getId)
                .containsExactly(idTieSecond.getId(), unratedBid.getId());
        assertThat(secondPage.bids().getContent()).noneMatch(Bid::getIsRecommended);
        assertThat(secondPage.bids().hasNext()).isTrue();
        assertThat(SqlCapture.SQL).anySatisfy(sql -> {
            String normalized = sql.toLowerCase();
            assertThat(normalized).contains("order by", "offset");
            assertThat(normalized).containsAnyOf("fetch first", "fetch next");
        });

        BidService.FreightBidsPage thirdPage =
                bidService.getBidsForFreightPage(freight.getId(), shipper, PageRequest.of(2, 2));
        assertThat(thirdPage.bids().getContent()).extracting(Bid::getId)
                .containsExactly(higherAmount.getId());
        assertThat(thirdPage.bids().getContent()).noneMatch(Bid::getIsRecommended);
        assertThat(List.of(firstPage.bids().getContent(), secondPage.bids().getContent(),
                thirdPage.bids().getContent()).stream().flatMap(List::stream)
                .map(Bid::getId).distinct()).hasSize(5);
    }

    @Test
    void intracityOrderingUsesAmountThenBidIdAndOwnerOrAdminScopeIsEnforced() {
        User shipper = user(User.Role.SHIPPER);
        User otherShipper = user(User.Role.SHIPPER);
        Freight freight = freight(shipper, Freight.ServiceType.INTRACITY);
        Bid laterId = bid(freight, user(User.Role.DRIVER), "25", Bid.BidStatus.PENDING);
        Bid earlierId = bid(freight, user(User.Role.DRIVER), "25.00", Bid.BidStatus.COUNTERED);
        Bid lowerAmount = bid(freight, user(User.Role.DRIVER), "20", Bid.BidStatus.REJECTED);
        entityManager.flush();

        assertThatThrownBy(() -> bidService.getBidsForFreightPage(
                freight.getId(), otherShipper, PageRequest.of(0, 20)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("own freight");

        BidService.FreightBidsPage ownerResult =
                bidService.getBidsForFreightPage(freight.getId(), shipper, PageRequest.of(0, 20));
        assertThat(ownerResult.bids().getContent()).extracting(Bid::getId)
                .containsExactly(lowerAmount.getId(), laterId.getId(), earlierId.getId());
        assertThat(bidService.getBidsForFreightPage(
                freight.getId(), user(User.Role.ADMIN), PageRequest.of(0, 20))
                .bids().getContent()).hasSize(3);
    }

    @Test
    void pageEndpointNormalizesBoundsSupportsAliasesAndKeepsLegacyArrayContract() throws Exception {
        User shipper = user(User.Role.SHIPPER);
        User admin = user(User.Role.ADMIN);
        Freight freight = freight(shipper, Freight.ServiceType.INTERCITY);
        User driver = user(User.Role.DRIVER);
        for (int amount = 1; amount <= 101; amount++) {
            bid(freight, driver, Integer.toString(amount), Bid.BidStatus.PENDING);
        }
        entityManager.flush();
        String ownerToken = "Bearer " + jwtService.generateToken(shipper);
        String adminToken = "Bearer " + jwtService.generateToken(admin);

        mvc.perform(get("/freight/{id}/bids/page", freight.getId()).header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.recommendedBidId").isNumber())
                .andExpect(jsonPath("$.content[0].isRecommended").value(true));

        mvc.perform(get("/cargo-requests/{id}/bids/page", freight.getId())
                        .queryParam("size", "100")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.content.length()").value(100));

        mvc.perform(get("/freight/{id}/bids/page", freight.getId())
                        .queryParam("page", "-2")
                        .queryParam("size", "0")
                        .header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1));

        mvc.perform(get("/freight/{id}/bids/page", freight.getId())
                        .queryParam("size", "10000")
                        .header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.content.length()").value(100));

        mvc.perform(get("/freight/{id}/bids/page", freight.getId())
                        .queryParam("page", "1")
                        .queryParam("size", "20")
                        .header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedBidId").value(nullValue()))
                .andExpect(jsonPath("$.content[0].isRecommended").value(false));

        User unauthorized = user(User.Role.SHIPPER);
        mvc.perform(get("/freight/{id}/bids/page", freight.getId())
                        .header("Authorization", "Bearer " + jwtService.generateToken(unauthorized)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/cargo-requests/{id}/bids", freight.getId())
                        .header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(100))
                .andExpect(jsonPath("$[0].isRecommended").value(true));
    }

    private User user(User.Role role) {
        String unique = UUID.randomUUID().toString();
        return userRepository.saveAndFlush(User.builder()
                .fullName("Bid pagination " + role)
                .email(unique + "@example.test")
                .phone(unique.substring(0, 12))
                .password("test-password")
                .role(role)
                .verificationStatus(true)
                .isActive(true)
                .build());
    }

    private Freight freight(User shipper, Freight.ServiceType serviceType) {
        return freightRepository.saveAndFlush(Freight.builder()
                .shipper(shipper)
                .pickupLocation("Addis Ababa")
                .destination("Adama")
                .materialType("general")
                .weightTons(BigDecimal.ONE)
                .budget(BigDecimal.valueOf(1000))
                .urgencyLevel("normal")
                .serviceType(serviceType)
                .priceType(Freight.PriceType.NEGOTIABLE)
                .status(Freight.FreightStatus.POSTED)
                .build());
    }

    private Bid bid(Freight freight, User driver, String amount, Bid.BidStatus status) {
        Vehicle vehicle = vehicleRepository.saveAndFlush(Vehicle.builder()
                .userId(driver.getId())
                .truckType("Test truck")
                .plateNumber("BID-" + UUID.randomUUID().toString().substring(0, 8))
                .capacity(10.0)
                .availabilityStatus("available")
                .build());
        return bidRepository.saveAndFlush(Bid.builder()
                .cargoRequest(freight)
                .driver(driver)
                .vehicle(vehicle)
                .amount(new BigDecimal(amount))
                .status(status)
                .build());
    }

    private void rate(User shipper, User driver, Freight freight, int value) {
        Vehicle vehicle = vehicleRepository.findAll().stream()
                .filter(candidate -> candidate.getUserId().equals(driver.getId()))
                .findFirst()
                .orElseThrow();
        Booking booking = bookingRepository.saveAndFlush(Booking.builder()
                .cargoRequest(freight)
                .vehicle(vehicle)
                .driver(driver)
                .bookingStatus(Booking.BookingStatus.COMPLETED)
                .commissionFee(BigDecimal.ZERO)
                .build());
        ratingRepository.saveAndFlush(Rating.builder()
                .booking(booking)
                .shipper(shipper)
                .driver(driver)
                .rater(shipper)
                .rating(value)
                .build());
    }

    public static class SqlCapture implements StatementInspector {
        static final List<String> SQL = new CopyOnWriteArrayList<>();

        @Override
        public String inspect(String sql) {
            if (sql.toLowerCase().startsWith("select")) {
                SQL.add(sql);
            }
            return sql;
        }
    }
}
