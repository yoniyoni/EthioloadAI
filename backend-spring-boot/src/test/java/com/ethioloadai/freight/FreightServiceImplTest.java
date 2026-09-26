package com.ethioloadai.freight;

import com.ethioloadai.exception.AuthorizationException;
import com.ethioloadai.freight.dto.*;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.mapper.FreightMapper;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.freight.service.FreightServiceImpl;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FreightServiceImpl}.
 *
 * Covers:
 * - Fix 7: getUserFreights is shipper/admin only.
 * - Fix 8: searchFreightsPaged uses Pageable and does not load all rows.
 * - Fix 5: calls use findByShipper_Id (exercised via mock verification).
 * - Freight ownership: only owner or admin can update/cancel.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FreightServiceImpl")
class FreightServiceImplTest {

    @Mock private FreightRepository freightRepository;
    @Mock private UserRepository userRepository;
    @Mock private FreightMapper freightMapper;

    @InjectMocks
    private FreightServiceImpl freightService;

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private User shipperUser(Long id) {
        User u = new User();
        u.setId(id);
        u.setRole(User.Role.SHIPPER);
        u.setFullName("Shipper");
        u.setPhone("+251900000001");
        u.setIsActive(true);
        return u;
    }

    private User driverUser(Long id) {
        User u = new User();
        u.setId(id);
        u.setRole(User.Role.DRIVER);
        u.setFullName("Driver");
        u.setPhone("+251900000002");
        u.setIsActive(true);
        return u;
    }

    private User adminUser(Long id) {
        User u = new User();
        u.setId(id);
        u.setRole(User.Role.ADMIN);
        u.setFullName("Admin");
        u.setPhone("+251900000003");
        u.setIsActive(true);
        return u;
    }

    private Freight postedFreight(Long id, User shipper) {
        Freight f = new Freight();
        f.setId(id);
        f.setShipper(shipper);
        f.setStatus(Freight.FreightStatus.POSTED);
        f.setPickupLocation("Addis Ababa");
        f.setDestination("Hawassa");
        f.setMaterialType("grain");
        f.setWeightTons(new BigDecimal("10.0"));
        f.setBudget(new BigDecimal("5000"));
        f.setUrgencyLevel("normal");
        return f;
    }

    // ── getUserFreights — shipper/admin only ─────────────────────────────────

    @Nested
    @DisplayName("getUserFreights")
    class GetUserFreights {

        @Test
        @DisplayName("shipper receives their own freight list")
        void shipperCanGetTheirFreights() {
            Long shipperId = 1L;
            User shipper = shipperUser(shipperId);
            List<Freight> freights = List.of(postedFreight(10L, shipper));
            FreightResponse resp = FreightResponse.builder().id(10L).build();

            when(userRepository.findById(shipperId)).thenReturn(Optional.of(shipper));
            when(freightRepository.findByShipper_IdOrderByCreatedAtDescIdDesc(shipperId)).thenReturn(freights);
            when(freightMapper.toResponse(freights.get(0))).thenReturn(resp);

            List<FreightResponse> result = freightService.getUserFreights(shipperId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(10L);
            // Verify the corrected repository method is called
            verify(freightRepository).findByShipper_IdOrderByCreatedAtDescIdDesc(shipperId);
        }

        @Test
        @DisplayName("admin can get any user's freight list")
        void adminCanGetFreights() {
            Long adminId = 99L;
            User admin = adminUser(adminId);

            when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
            when(freightRepository.findByShipper_IdOrderByCreatedAtDescIdDesc(adminId)).thenReturn(List.of());

            List<FreightResponse> result = freightService.getUserFreights(adminId);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("driver is rejected with AuthorizationException")
        void driverCannotGetFreights() {
            Long driverId = 2L;
            User driver = driverUser(driverId);
            when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));

            assertThatThrownBy(() -> freightService.getUserFreights(driverId))
                    .isInstanceOf(AuthorizationException.class);

            verify(freightRepository, never()).findByShipper_IdOrderByCreatedAtDescIdDesc(any());
        }
    }

    // ── searchFreightsPaged — pagination (Fix 8) ─────────────────────────────

    @Nested
    @DisplayName("searchFreightsPaged")
    class SearchFreightsPaged {

        @Test
        @DisplayName("returns a Page, not a List — delegates to paginated repository method")
        void returnsPaginatedResults() {
            FreightFilterRequest filter = new FreightFilterRequest();
            Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));

            User shipper = shipperUser(1L);
            Freight f = postedFreight(5L, shipper);
            FreightResponse resp = FreightResponse.builder().id(5L).build();

            Page<Freight> freightPage = new PageImpl<>(List.of(f), pageable, 1);
            when(freightRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(freightPage);
            when(freightMapper.toResponse(f)).thenReturn(resp);

            Page<FreightResponse> result = freightService.searchFreightsPaged(filter, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getId()).isEqualTo(5L);

            // Verify paginated repo method is called — never the unpaged findAll
            verify(freightRepository).findAll(any(Specification.class), eq(pageable));
        }

        @Test
        @DisplayName("page 2 with size 5 is forwarded to repository")
        void paginationParametersAreForwarded() {
            FreightFilterRequest filter = new FreightFilterRequest();
            Pageable pageable = PageRequest.of(2, 5);

            when(freightRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(Page.empty(pageable));

            Page<FreightResponse> result = freightService.searchFreightsPaged(filter, pageable);

            assertThat(result.getNumber()).isEqualTo(2);
            assertThat(result.getSize()).isEqualTo(5);
            verify(freightRepository).findAll(any(Specification.class), eq(pageable));
        }

        @Test
        @DisplayName("empty result returns empty page without error")
        void emptyResultIsHandledGracefully() {
            FreightFilterRequest filter = new FreightFilterRequest();
            Pageable pageable = PageRequest.of(0, 20);

            when(freightRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(Page.empty(pageable));

            Page<FreightResponse> result = freightService.searchFreightsPaged(filter, pageable);
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0);
        }
    }

    // ── cancelFreight — ownership (Fix 7 ownership check) ───────────────────

    @Nested
    @DisplayName("cancelFreight ownership")
    class CancelFreightOwnership {

        @Test
        @DisplayName("owner can cancel their own POSTED freight")
        void ownerCanCancelOwnFreight() {
            Long shipperId = 1L;
            User shipper = shipperUser(shipperId);
            Freight freight = postedFreight(10L, shipper);

            when(freightRepository.findById(10L)).thenReturn(Optional.of(freight));
            when(userRepository.findById(shipperId)).thenReturn(Optional.of(shipper));
            when(freightRepository.save(any(Freight.class))).thenReturn(freight);

            freightService.cancelFreight(10L, shipperId);

            assertThat(freight.getStatus()).isEqualTo(Freight.FreightStatus.CANCELLED);
        }

        @Test
        @DisplayName("non-owner driver is rejected with OwnershipException")
        void nonOwnerIsRejected() {
            Long ownerId = 1L;
            Long otherDriverId = 5L;

            User owner = shipperUser(ownerId);
            User otherUser = driverUser(otherDriverId);
            Freight freight = postedFreight(10L, owner);

            when(freightRepository.findById(10L)).thenReturn(Optional.of(freight));
            when(userRepository.findById(otherDriverId)).thenReturn(Optional.of(otherUser));

            assertThatThrownBy(() -> freightService.cancelFreight(10L, otherDriverId))
                    .isInstanceOf(AuthorizationException.OwnershipException.class);
        }

        @Test
        @DisplayName("admin can cancel any freight")
        void adminCanCancelAnyFreight() {
            Long adminId = 99L;
            Long ownerId = 1L;
            User owner = shipperUser(ownerId);
            User admin = adminUser(adminId);
            Freight freight = postedFreight(10L, owner);

            when(freightRepository.findById(10L)).thenReturn(Optional.of(freight));
            when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
            when(freightRepository.save(any(Freight.class))).thenReturn(freight);

            freightService.cancelFreight(10L, adminId);
            assertThat(freight.getStatus()).isEqualTo(Freight.FreightStatus.CANCELLED);
        }
    }
}
