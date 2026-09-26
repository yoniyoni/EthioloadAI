package com.ethioloadai.dispute;

import com.ethioloadai.booking.entity.Booking;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.dispute.dto.CreateDisputeRequest;
import com.ethioloadai.dispute.dto.DisputeListResponse;
import com.ethioloadai.dispute.dto.DisputeResponse;
import com.ethioloadai.dispute.dto.ResolveDisputeRequest;
import com.ethioloadai.dispute.entity.Dispute;
import com.ethioloadai.dispute.entity.DisputeStatus;
import com.ethioloadai.dispute.exception.DisputeException.*;
import com.ethioloadai.dispute.repository.DisputeRepository;
import com.ethioloadai.dispute.service.DisputeServiceImpl;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisputeServiceImplTest {

    @Mock
    private DisputeRepository disputeRepository;

    @Mock
    private FreightRepository freightRepository;

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private DisputeServiceImpl disputeService;

    private User shipper;
    private User driver;
    private User thirdParty;
    private User admin;
    private Freight freight;
    private Booking booking;

    @BeforeEach
    void setUp() {
        shipper = User.builder()
                .id(1L)
                .fullName("Abebe Shipper")
                .email("shipper@example.com")
                .role(User.Role.SHIPPER)
                .build();

        driver = User.builder()
                .id(2L)
                .fullName("Kebede Driver")
                .email("driver@example.com")
                .role(User.Role.DRIVER)
                .build();

        thirdParty = User.builder()
                .id(99L)
                .fullName("Intruder")
                .email("intruder@example.com")
                .role(User.Role.SHIPPER)
                .build();

        admin = User.builder()
                .id(100L)
                .fullName("Admin User")
                .email("admin@example.com")
                .role(User.Role.ADMIN)
                .build();

        freight = Freight.builder()
                .id(10L)
                .shipper(shipper)
                .matchedDriver(driver)
                .materialType("Electronics")
                .build();

        booking = Booking.builder()
                .id(20L)
                .cargoRequest(freight)
                .driver(driver)
                .bookingStatus(Booking.BookingStatus.DELIVERED)
                .estimatedPrice(new BigDecimal("25000.00"))
                .build();
    }

    @Nested
    @DisplayName("Filing Disputes (POST /api/disputes)")
    class CreateDisputeTests {

        @Test
        @DisplayName("Shipper can file a dispute successfully")
        void createDispute_byShipper_success() {
            CreateDisputeRequest request = new CreateDisputeRequest(10L, "Cargo damaged", "Water damage to boxes");
            when(freightRepository.findById(10L)).thenReturn(Optional.of(freight));
            when(bookingRepository.findByCargoRequest_Id(10L)).thenReturn(List.of(booking));
            when(disputeRepository.existsByFreight_IdAndStatusIn(eq(10L), anyCollection())).thenReturn(false);

            Dispute saved = Dispute.builder()
                    .id(101L)
                    .freight(freight)
                    .booking(booking)
                    .reportedBy(shipper)
                    .reason("Cargo damaged")
                    .description("Water damage to boxes")
                    .status(DisputeStatus.OPEN)
                    .createdAt(LocalDateTime.now())
                    .build();

            when(disputeRepository.save(any(Dispute.class))).thenReturn(saved);

            DisputeResponse response = disputeService.createDispute(request, shipper);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(101L);
            assertThat(response.freightId()).isEqualTo(10L);
            assertThat(response.bookingId()).isEqualTo(20L);
            assertThat(response.reportedById()).isEqualTo(1L);
            assertThat(response.reason()).isEqualTo("Cargo damaged");
            assertThat(response.status()).isEqualTo("open");
        }

        @Test
        @DisplayName("Assigned driver can file a dispute successfully")
        void createDispute_byDriver_success() {
            CreateDisputeRequest request = new CreateDisputeRequest(10L, "Payment issue", "Shipper refuses to confirm receipt");
            when(freightRepository.findById(10L)).thenReturn(Optional.of(freight));
            when(bookingRepository.findByCargoRequest_Id(10L)).thenReturn(List.of(booking));
            when(disputeRepository.existsByFreight_IdAndStatusIn(eq(10L), anyCollection())).thenReturn(false);

            Dispute saved = Dispute.builder()
                    .id(102L)
                    .freight(freight)
                    .booking(booking)
                    .reportedBy(driver)
                    .reason("Payment issue")
                    .description("Shipper refuses to confirm receipt")
                    .status(DisputeStatus.OPEN)
                    .createdAt(LocalDateTime.now())
                    .build();

            when(disputeRepository.save(any(Dispute.class))).thenReturn(saved);

            DisputeResponse response = disputeService.createDispute(request, driver);

            assertThat(response.reportedById()).isEqualTo(2L);
            assertThat(response.status()).isEqualTo("open");
        }

        @Test
        @DisplayName("Admin cannot file a dispute for users")
        void createDispute_byAdmin_throwsUnauthorized() {
            CreateDisputeRequest request = new CreateDisputeRequest(10L, "Admin filing", "Notes");

            assertThatThrownBy(() -> disputeService.createDispute(request, admin))
                    .isInstanceOf(UnauthorizedDisputeAccessException.class)
                    .hasMessageContaining("Administrators cannot file disputes");

            verify(disputeRepository, never()).save(any());
        }

        @Test
        @DisplayName("Third party is rejected with 403 Forbidden (IDOR prevention)")
        void createDispute_byThirdParty_throwsUnauthorized() {
            CreateDisputeRequest request = new CreateDisputeRequest(10L, "Illegal claim", "Hacking attempt");
            when(freightRepository.findById(10L)).thenReturn(Optional.of(freight));
            when(bookingRepository.findByCargoRequest_Id(10L)).thenReturn(List.of(booking));

            assertThatThrownBy(() -> disputeService.createDispute(request, thirdParty))
                    .isInstanceOf(UnauthorizedDisputeAccessException.class)
                    .hasMessageContaining("Only the freight shipper or assigned driver may file a dispute");

            verify(disputeRepository, never()).save(any());
        }

        @Test
        @DisplayName("Duplicate active dispute is rejected with 409 Conflict")
        void createDispute_duplicateActiveDispute_throwsDuplicateDisputeException() {
            CreateDisputeRequest request = new CreateDisputeRequest(10L, "Second dispute", "Another complaint");
            when(freightRepository.findById(10L)).thenReturn(Optional.of(freight));
            when(bookingRepository.findByCargoRequest_Id(10L)).thenReturn(List.of(booking));
            when(disputeRepository.existsByFreight_IdAndStatusIn(eq(10L), anyCollection())).thenReturn(true);

            assertThatThrownBy(() -> disputeService.createDispute(request, shipper))
                    .isInstanceOf(DuplicateDisputeException.class)
                    .hasMessageContaining("An active dispute is already open");

            verify(disputeRepository, never()).save(any());
        }

        @Test
        @DisplayName("Filing dispute on non-existent freight returns 404 Not Found")
        void createDispute_freightNotFound_throwsNotFound() {
            CreateDisputeRequest request = new CreateDisputeRequest(999L, "Missing freight", "Detail");
            when(freightRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> disputeService.createDispute(request, shipper))
                    .isInstanceOf(DisputeNotFoundException.class)
                    .hasMessageContaining("Freight not found with ID: 999");
        }
    }

    @Nested
    @DisplayName("Retrieving Disputes (GET /api/disputes?freightId={id})")
    class GetDisputesTests {

        @Test
        @DisplayName("Shipper can retrieve disputes for their freight")
        void getDisputesByFreightId_byShipper_success() {
            when(freightRepository.findById(10L)).thenReturn(Optional.of(freight));
            when(bookingRepository.findByCargoRequest_Id(10L)).thenReturn(List.of(booking));

            Dispute d = Dispute.builder()
                    .id(1L)
                    .freight(freight)
                    .reportedBy(shipper)
                    .reason("Issue")
                    .status(DisputeStatus.OPEN)
                    .createdAt(LocalDateTime.now())
                    .build();

            when(disputeRepository.findByFreight_IdOrderByCreatedAtDesc(10L)).thenReturn(List.of(d));

            DisputeListResponse response = disputeService.getDisputesByFreightId(10L, shipper);

            assertThat(response.total()).isEqualTo(1);
            assertThat(response.disputes()).hasSize(1);
            assertThat(response.disputes().get(0).reason()).isEqualTo("Issue");
        }

        @Test
        @DisplayName("Admin can retrieve disputes for any freight")
        void getDisputesByFreightId_byAdmin_success() {
            when(freightRepository.findById(10L)).thenReturn(Optional.of(freight));
            when(bookingRepository.findByCargoRequest_Id(10L)).thenReturn(List.of(booking));
            when(disputeRepository.findByFreight_IdOrderByCreatedAtDesc(10L)).thenReturn(Collections.emptyList());

            DisputeListResponse response = disputeService.getDisputesByFreightId(10L, admin);

            assertThat(response.total()).isZero();
            assertThat(response.disputes()).isEmpty();
        }

        @Test
        @DisplayName("Third party cannot view disputes (403 Forbidden)")
        void getDisputesByFreightId_byThirdParty_throwsUnauthorized() {
            when(freightRepository.findById(10L)).thenReturn(Optional.of(freight));
            when(bookingRepository.findByCargoRequest_Id(10L)).thenReturn(List.of(booking));

            assertThatThrownBy(() -> disputeService.getDisputesByFreightId(10L, thirdParty))
                    .isInstanceOf(UnauthorizedDisputeAccessException.class)
                    .hasMessageContaining("You are not authorized to view disputes");
        }

        @Test
        @DisplayName("Admin can list all disputes across the platform")
        void getAllDisputes_byAdmin_success() {
            Dispute d1 = Dispute.builder().id(1L).freight(freight).status(DisputeStatus.OPEN).build();
            Dispute d2 = Dispute.builder().id(2L).freight(freight).status(DisputeStatus.RESOLVED).build();
            when(disputeRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(d1, d2));

            DisputeListResponse response = disputeService.getAllDisputes(admin);

            assertThat(response.total()).isEqualTo(2);
            assertThat(response.disputes()).hasSize(2);
        }

        @Test
        @DisplayName("Non-admin cannot list all disputes (403 Forbidden)")
        void getAllDisputes_byNonAdmin_throwsUnauthorized() {
            assertThatThrownBy(() -> disputeService.getAllDisputes(shipper))
                    .isInstanceOf(UnauthorizedDisputeAccessException.class)
                    .hasMessageContaining("Only administrators may list all disputes");
        }
    }

    @Nested
    @DisplayName("Resolving Disputes (PATCH /api/disputes/{id}/resolve)")
    class ResolveDisputeTests {

        private Dispute openDispute;

        @BeforeEach
        void initDispute() {
            openDispute = Dispute.builder()
                    .id(500L)
                    .freight(freight)
                    .booking(booking)
                    .reportedBy(shipper)
                    .reason("Delayed cargo")
                    .status(DisputeStatus.OPEN)
                    .createdAt(LocalDateTime.now().minusDays(1))
                    .build();
        }

        @Test
        @DisplayName("Admin can start investigation ('investigating' -> UNDER_REVIEW)")
        void resolveDispute_investigating_transitionsToUnderReview() {
            when(disputeRepository.findById(500L)).thenReturn(Optional.of(openDispute));
            when(disputeRepository.save(any(Dispute.class))).thenAnswer(i -> i.getArgument(0));

            ResolveDisputeRequest request = new ResolveDisputeRequest("investigating", "Contacted driver for GPS logs", null);

            DisputeResponse response = disputeService.resolveDispute(500L, request, admin);

            assertThat(response.status()).isEqualTo("under_review");
            assertThat(response.resolution()).isEqualTo("investigating");
            assertThat(response.adminNotes()).isEqualTo("Contacted driver for GPS logs");
        }

        @Test
        @DisplayName("Admin can refund shipper ('refund' -> RESOLVED)")
        void resolveDispute_refund_transitionsToResolved() {
            when(disputeRepository.findById(500L)).thenReturn(Optional.of(openDispute));
            when(disputeRepository.save(any(Dispute.class))).thenAnswer(i -> i.getArgument(0));

            ResolveDisputeRequest request = new ResolveDisputeRequest("refund", "Full refund approved", new BigDecimal("25000.00"));

            DisputeResponse response = disputeService.resolveDispute(500L, request, admin);

            assertThat(response.status()).isEqualTo("resolved");
            assertThat(response.resolution()).isEqualTo("refund");
            assertThat(response.refundAmount()).isEqualByComparingTo("25000.00");
            assertThat(response.resolvedAt()).isNotNull();
        }

        @Test
        @DisplayName("Admin can release funds to driver ('release' -> RESOLVED)")
        void resolveDispute_release_transitionsToResolved() {
            when(disputeRepository.findById(500L)).thenReturn(Optional.of(openDispute));
            when(disputeRepository.save(any(Dispute.class))).thenAnswer(i -> i.getArgument(0));

            ResolveDisputeRequest request = new ResolveDisputeRequest("release", "Driver fulfilled terms", BigDecimal.ZERO);

            DisputeResponse response = disputeService.resolveDispute(500L, request, admin);

            assertThat(response.status()).isEqualTo("resolved");
            assertThat(response.resolution()).isEqualTo("release");
        }

        @Test
        @DisplayName("Admin can split amount ('split' -> RESOLVED)")
        void resolveDispute_split_transitionsToResolved() {
            when(disputeRepository.findById(500L)).thenReturn(Optional.of(openDispute));
            when(disputeRepository.save(any(Dispute.class))).thenAnswer(i -> i.getArgument(0));

            ResolveDisputeRequest request = new ResolveDisputeRequest("split", "50% refund agreed", new BigDecimal("12500.00"));

            DisputeResponse response = disputeService.resolveDispute(500L, request, admin);

            assertThat(response.status()).isEqualTo("resolved");
            assertThat(response.resolution()).isEqualTo("split");
            assertThat(response.refundAmount()).isEqualByComparingTo("12500.00");
        }

        @Test
        @DisplayName("Non-admin cannot resolve dispute (403 Forbidden)")
        void resolveDispute_byNonAdmin_throwsUnauthorized() {
            ResolveDisputeRequest request = new ResolveDisputeRequest("refund", "Notes", null);

            assertThatThrownBy(() -> disputeService.resolveDispute(500L, request, shipper))
                    .isInstanceOf(UnauthorizedDisputeAccessException.class)
                    .hasMessageContaining("Only administrators may resolve disputes");
        }

        @Test
        @DisplayName("Resolving an already resolved dispute throws 409 Conflict")
        void resolveDispute_alreadyResolved_throwsConflict() {
            openDispute.setStatus(DisputeStatus.RESOLVED);
            when(disputeRepository.findById(500L)).thenReturn(Optional.of(openDispute));

            ResolveDisputeRequest request = new ResolveDisputeRequest("release", "Trying again", null);

            assertThatThrownBy(() -> disputeService.resolveDispute(500L, request, admin))
                    .isInstanceOf(DisputeAlreadyResolvedException.class)
                    .hasMessageContaining("is already resolved");
        }

        @Test
        @DisplayName("Invalid resolution string throws 400 Bad Request")
        void resolveDispute_invalidResolution_throwsInvalidOperation() {
            when(disputeRepository.findById(500L)).thenReturn(Optional.of(openDispute));

            ResolveDisputeRequest request = new ResolveDisputeRequest("unsupported_action", "Notes", null);

            assertThatThrownBy(() -> disputeService.resolveDispute(500L, request, admin))
                    .isInstanceOf(InvalidDisputeOperationException.class)
                    .hasMessageContaining("Invalid resolution: 'unsupported_action'");
        }
    }
}
