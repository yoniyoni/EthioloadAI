package com.ethioloadai.freight;

import com.ethioloadai.freight.dto.FreightResponse;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.freight.service.FreightService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.ethioloadai.freight.FreightHistoryPaginationIntegrationTest$SqlCapture"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class FreightHistoryPaginationIntegrationTest {
    @Autowired private FreightRepository freightRepository;
    @Autowired private FreightService freightService;
    @Autowired private UserRepository userRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private jakarta.persistence.EntityManager entityManager;
    @Autowired private JwtService jwtService;
    @Autowired private MockMvc mvc;

    @BeforeEach
    void clearSqlCapture() {
        SqlCapture.SQL.clear();
    }

    @Test
    void pageBoundsAndCreatedAtIdOrderingAreStableAcrossPages() throws Exception {
        User shipper = user(User.Role.SHIPPER);
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 105; i++) {
            ids.add(freight(shipper, "Pickup " + i, "grain", Freight.FreightStatus.POSTED).getId());
        }
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE freight SET created_at = :createdAt WHERE shipper_id = :shipperId")
                .setParameter("createdAt", LocalDateTime.of(2026, 1, 1, 12, 0))
                .setParameter("shipperId", shipper.getId())
                .executeUpdate();
        entityManager.clear();

        String token = bearer(shipper);
        mvc.perform(get("/freight/my-freights/page").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.totalElements").value(105))
                .andExpect(jsonPath("$.totalPages").value(6))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content.length()").value(20));

        mvc.perform(get("/freight/my-freights/page").queryParam("size", "10000")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.content.length()").value(100));

        mvc.perform(get("/freight/my-freights/page").queryParam("page", "-4")
                        .queryParam("size", "0")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(1));

        List<Long> actualIds = new ArrayList<>();
        int page = 0;
        Page<FreightResponse> result;
        do {
            result = freightService.getUserFreightsPaged(
                    shipper.getId(), null, null, null,
                    PageRequest.of(page++, 20,
                            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
            actualIds.addAll(result.getContent().stream().map(FreightResponse::getId).toList());
        } while (result.hasNext());
        assertThat(actualIds).containsExactlyElementsOf(ids.reversed());
        assertThat(actualIds).doesNotHaveDuplicates();
    }

    @Test
    void statusMaterialAndLiteralCaseInsensitiveSearchFiltersAreAppliedBeforePaging() throws Exception {
        User shipper = user(User.Role.SHIPPER);
        freight(shipper, "literal %_\\ depot", "grain", Freight.FreightStatus.POSTED);
        freight(shipper, "other route", "grain", Freight.FreightStatus.MATCHED);
        freight(shipper, "another route", "cement", Freight.FreightStatus.IN_TRANSIT);
        freight(shipper, "completed route", "grain", Freight.FreightStatus.COMPLETED);
        freight(shipper, "cancelled route", "grain", Freight.FreightStatus.CANCELLED);
        entityManager.flush();
        String token = bearer(shipper);

        mvc.perform(get("/freight/my-freights/page")
                        .queryParam("status", "posted", "matched", "in_transit")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));

        mvc.perform(get("/freight/my-freights/page")
                        .queryParam("status", "posted", "matched", "in_transit")
                        .queryParam("materialType", "grain")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].materialType").value("grain"));

        mvc.perform(get("/freight/my-freights/page").queryParam("search", "LITERAL")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(get("/freight/my-freights/page").queryParam("search", "%_\\")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].pickupLocation").value("literal %_\\ depot"));
    }

    @Test
    void freightRowsAreScopedToAuthenticatedOwnerAndToOneAssociationsArePageFetched() throws Exception {
        User shipper = user(User.Role.SHIPPER);
        User otherShipper = user(User.Role.SHIPPER);
        User driver = user(User.Role.DRIVER);
        Vehicle vehicle = vehicleRepository.saveAndFlush(Vehicle.builder()
                .userId(driver.getId())
                .truckType("Test vehicle")
                .plateNumber("VH-" + UUID.randomUUID().toString().substring(0, 8))
                .capacity(10.0)
                .build());
        for (int i = 0; i < 8; i++) {
            Freight item = freight(shipper, "Owner row " + i, "grain", Freight.FreightStatus.POSTED);
            item.setMatchedDriver(driver);
            item.setMatchedVehicle(vehicle);
            freightRepository.save(item);
        }
        freight(otherShipper, "Other user's row", "grain", Freight.FreightStatus.POSTED);
        entityManager.flush();
        entityManager.clear();
        SqlCapture.SQL.clear();

        mvc.perform(get("/freight/my-freights/page").queryParam("size", "5")
                        .header("Authorization", bearer(shipper)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.totalElements").value(8))
                .andExpect(jsonPath("$.content[0].shipperId").value(shipper.getId()))
                .andExpect(jsonPath("$.content[0].shipperName").value(shipper.getFullName()))
                .andExpect(jsonPath("$.content[0].matchedDriverId").value(driver.getId()))
                .andExpect(jsonPath("$.content[0].matchedDriverName").value(driver.getFullName()))
                .andExpect(jsonPath("$.content[0].matchedVehicleId").value(vehicle.getId()));

        entityManager.clear();
        SqlCapture.SQL.clear();
        Page<FreightResponse> mappedPage = freightService.getUserFreightsPaged(
                shipper.getId(), null, null, null,
                PageRequest.of(0, 5, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        assertThat(mappedPage.getContent()).hasSize(5);

        List<String> freightSelects = SqlCapture.SQL.stream()
                .map(String::toLowerCase)
                .filter(sql -> sql.contains(" from freight ") && !sql.contains("count("))
                .toList();
        assertThat(freightSelects).hasSize(1);
        assertThat(freightSelects.getFirst()).contains("join users", "join vehicles");
        assertThat(freightSelects.getFirst())
                .matches(sql -> sql.contains("fetch first") || sql.contains(" limit "));
        assertThat(SqlCapture.SQL.stream().map(String::toLowerCase)
                .filter(sql -> sql.contains(" from users "))).hasSize(1);

        User driverCaller = user(User.Role.DRIVER);
        mvc.perform(get("/freight/my-freights/page")
                        .header("Authorization", bearer(driverCaller)))
                .andExpect(status().isForbidden());
    }

    @Test
    void emptyResultsAndUnknownStatusesKeepThePageResponseShape() throws Exception {
        User shipper = user(User.Role.SHIPPER);
        freight(shipper, "Posted freight", "grain", Freight.FreightStatus.POSTED);
        freight(shipper, "Completed freight", "cement", Freight.FreightStatus.COMPLETED);
        entityManager.flush();
        String token = bearer(shipper);

        mvc.perform(get("/freight/my-freights/page").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.last").value(true));

        mvc.perform(get("/freight/my-freights/page").queryParam("status", "")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mvc.perform(get("/freight/my-freights/page").queryParam("status", "not-a-status")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));

        mvc.perform(get("/freight/my-freights/page")
                        .queryParam("status", "posted", "not-a-status")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("posted"));
    }

    private User user(User.Role role) {
        String suffix = UUID.randomUUID().toString();
        return userRepository.saveAndFlush(User.builder()
                .fullName("Freight page " + role)
                .phone("+" + suffix.substring(0, 12))
                .password("test-password")
                .role(role)
                .verificationStatus(true)
                .isActive(true)
                .build());
    }

    private Freight freight(User shipper, String pickup, String materialType, Freight.FreightStatus status) {
        return freightRepository.saveAndFlush(Freight.builder()
                .shipper(shipper)
                .pickupLocation(pickup)
                .destination("Destination")
                .materialType(materialType)
                .cargoDescription("Cargo details")
                .weightTons(BigDecimal.ONE)
                .budget(BigDecimal.valueOf(1000))
                .urgencyLevel("normal")
                .status(status)
                .build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
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
