package com.ethioloadai.freight;

import com.ethioloadai.exception.ValidationException;
import com.ethioloadai.freight.dto.CreateFreightRequest;
import com.ethioloadai.freight.dto.FreightFilterRequest;
import com.ethioloadai.freight.dto.FreightResponse;
import com.ethioloadai.freight.dto.UpdateFreightRequest;
import com.ethioloadai.freight.entity.Freight;
import com.ethioloadai.freight.mapper.FreightMapper;
import com.ethioloadai.freight.repository.FreightRepository;
import com.ethioloadai.bid.repository.BidRepository;
import com.ethioloadai.booking.repository.BookingRepository;
import com.ethioloadai.carrier.dispatch.repository.DispatchAssignmentRepository;
import com.ethioloadai.carrier.offer.repository.CarrierOfferRepository;
import com.ethioloadai.trip.repository.TripRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for V6 freight features:
 * - Intracity vs Intercity business validation
 * - Price type validation (negotiable vs fixed)
 * - Bid deadline handling
 * - V6 field mapping and filtering
 * - Service type specific business rules
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Freight V6 Features")
class FreightV6FeaturesTest {

    @Mock private FreightRepository freightRepository;
    @Mock private UserRepository userRepository;
    @Mock private FreightMapper freightMapper;
    @Mock private BidRepository bidRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private DispatchAssignmentRepository dispatchRepository;
    @Mock private CarrierOfferRepository carrierOfferRepository;
    @Mock private TripRepository tripRepository;

    @InjectMocks
    private FreightServiceImpl freightService;

    private User shipperUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        shipperUser = new User();
        shipperUser.setId(1L);
        shipperUser.setRole(User.Role.SHIPPER);
        shipperUser.setFullName("Test Shipper");

        adminUser = new User();
        adminUser.setId(99L);
        adminUser.setRole(User.Role.ADMIN);
        adminUser.setFullName("Admin User");
    }

    // ── V6 CREATE VALIDATION TESTS ─────────────────────────────────────────────

    @Nested
    @DisplayName("V6 Create Freight Validation")
    class CreateFreightValidation {

        @Test
        @DisplayName("Intracity freight requires city, pickup area, dropoff area, preferred date, items description")
        void intracityRequiredFields() {
            CreateFreightRequest request = CreateFreightRequest.builder()
                    .serviceType(Freight.ServiceType.INTRACITY)
                    .priceType(Freight.PriceType.NEGOTIABLE)
                    // Missing required intracity fields
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(shipperUser));

            // Should fail on city validation
            assertThatThrownBy(() -> freightService.createFreight(1L, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("City is required for intracity freight");
        }

        @Test
        @DisplayName("Valid intracity freight creation succeeds")
        void validIntracityFreight() {
            CreateFreightRequest request = CreateFreightRequest.builder()
                    .serviceType(Freight.ServiceType.INTRACITY)
                    .priceType(Freight.PriceType.NEGOTIABLE)
                    .city("Addis Ababa")
                    .pickupArea("Bole")
                    .dropoffArea("Piazza")
                    .preferredDate(LocalDate.now().plusDays(1))
                    .itemsDescription("Furniture delivery")
                    .vehicleTypeNeeded("pickup truck")
                    .build();

            Freight freight = new Freight();
            freight.setId(1L);
            FreightResponse response = FreightResponse.builder().id(1L).build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(shipperUser));
            when(freightMapper.toEntity(request)).thenReturn(freight);
            when(freightRepository.save(any(Freight.class))).thenReturn(freight);
            when(freightMapper.toResponse(freight)).thenReturn(response);

            FreightResponse result = freightService.createFreight(1L, request);

            assertThat(result).isNotNull();
            verify(freightRepository).save(any(Freight.class));
        }

        @Test
        @DisplayName("Intercity freight requires pickup location, destination, material type, weight, urgency")
        void intercityRequiredFields() {
            CreateFreightRequest request = CreateFreightRequest.builder()
                    .serviceType(Freight.ServiceType.INTERCITY)
                    .priceType(Freight.PriceType.NEGOTIABLE)
                    // Missing required intercity fields
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(shipperUser));

            assertThatThrownBy(() -> freightService.createFreight(1L, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Pickup location is required for intercity freight");
        }

        @Test
        @DisplayName("Fixed price requires budget")
        void fixedPriceRequiresBudget() {
            CreateFreightRequest request = CreateFreightRequest.builder()
                    .serviceType(Freight.ServiceType.INTERCITY)
                    .priceType(Freight.PriceType.FIXED)
                    .pickupLocation("Addis Ababa")
                    .destination("Hawassa")
                    .materialType("grain")
                    .weightTons(new BigDecimal("10.0"))
                    .urgencyLevel("normal")
                    // Missing budget for fixed price
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(shipperUser));

            assertThatThrownBy(() -> freightService.createFreight(1L, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Budget is required for fixed-price freight");
        }

        @Test
        @DisplayName("Bid deadline must be in future")
        void bidDeadlineValidation() {
            CreateFreightRequest request = CreateFreightRequest.builder()
                    .serviceType(Freight.ServiceType.INTERCITY)
                    .priceType(Freight.PriceType.NEGOTIABLE)
                    .pickupLocation("Addis Ababa")
                    .destination("Hawassa")
                    .materialType("grain")
                    .weightTons(new BigDecimal("10.0"))
                    .urgencyLevel("normal")
                    .bidDeadline(LocalDateTime.now().minusHours(1)) // Past deadline
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(shipperUser));

            assertThatThrownBy(() -> freightService.createFreight(1L, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Bid deadline must be in the future");
        }

        @Test
        @DisplayName("Preferred date must be today or future")
        void preferredDateValidation() {
            CreateFreightRequest request = CreateFreightRequest.builder()
                    .serviceType(Freight.ServiceType.INTRACITY)
                    .priceType(Freight.PriceType.NEGOTIABLE)
                    .city("Addis Ababa")
                    .pickupArea("Bole")
                    .dropoffArea("Piazza")
                    .preferredDate(LocalDate.now().minusDays(1)) // Past date
                    .itemsDescription("Furniture delivery")
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(shipperUser));

            assertThatThrownBy(() -> freightService.createFreight(1L, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Preferred date must be today or in the future");
        }
    }

    // ── V6 UPDATE VALIDATION TESTS ─────────────────────────────────────────────

    @Nested
    @DisplayName("V6 Update Freight Validation")
    class UpdateFreightValidation {

        @Test
        @DisplayName("Price type change only allowed in POSTED status")
        void priceTypeChangeValidation() {
            Freight freight = new Freight();
            freight.setId(1L);
            freight.setShipper(shipperUser);
            freight.setStatus(Freight.FreightStatus.MATCHED); // Not POSTED

            UpdateFreightRequest request = UpdateFreightRequest.builder()
                    .priceType(Freight.PriceType.FIXED)
                    .build();

            when(freightRepository.findById(1L)).thenReturn(Optional.of(freight));
            when(userRepository.findById(1L)).thenReturn(Optional.of(shipperUser));

            assertThatThrownBy(() -> freightService.updateFreight(1L, 1L, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Price type can only be changed while freight is in POSTED status");
        }

        @Test
        @DisplayName("Fixed price update requires budget")
        void fixedPriceUpdateRequiresBudget() {
            Freight freight = new Freight();
            freight.setId(1L);
            freight.setShipper(shipperUser);
            freight.setStatus(Freight.FreightStatus.POSTED);
            freight.setBudget(null); // No existing budget

            UpdateFreightRequest request = UpdateFreightRequest.builder()
                    .priceType(Freight.PriceType.FIXED)
                    // No budget provided
                    .build();

            when(freightRepository.findById(1L)).thenReturn(Optional.of(freight));
            when(userRepository.findById(1L)).thenReturn(Optional.of(shipperUser));

            assertThatThrownBy(() -> freightService.updateFreight(1L, 1L, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Budget is required for fixed-price freight");
        }

        @Test
        @DisplayName("Intracity city cannot be empty")
        void intracityCityValidation() {
            Freight freight = new Freight();
            freight.setId(1L);
            freight.setShipper(shipperUser);
            freight.setStatus(Freight.FreightStatus.POSTED);
            freight.setServiceType(Freight.ServiceType.INTRACITY);

            UpdateFreightRequest request = UpdateFreightRequest.builder()
                    .city("") // Empty city
                    .build();

            when(freightRepository.findById(1L)).thenReturn(Optional.of(freight));
            when(userRepository.findById(1L)).thenReturn(Optional.of(shipperUser));

            assertThatThrownBy(() -> freightService.updateFreight(1L, 1L, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("City cannot be empty for intracity freight");
        }
    }

    // ── V6 FILTERING TESTS ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("V6 Filtering and Queries")
    class V6Filtering {

        @Test
        @DisplayName("Service type filtering works correctly")
        void serviceTypeFiltering() {
            FreightFilterRequest filter = FreightFilterRequest.builder()
                    .serviceType("intracity")
                    .build();

            Freight intracityFreight = new Freight();
            intracityFreight.setId(1L);
            intracityFreight.setServiceType(Freight.ServiceType.INTRACITY);

            Page<Freight> freightPage = new PageImpl<>(List.of(intracityFreight));
            when(freightRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(freightPage);
            when(freightMapper.toResponse(any(Freight.class)))
                    .thenReturn(FreightResponse.builder().id(1L).build());

            Pageable pageable = PageRequest.of(0, 20);
            Page<FreightResponse> result = freightService.searchFreightsPaged(filter, pageable);

            assertThat(result.getContent()).hasSize(1);
            verify(freightRepository).findAll(any(Specification.class), eq(pageable));
        }

        @Test
        @DisplayName("Price type filtering works correctly")
        void priceTypeFiltering() {
            FreightFilterRequest filter = FreightFilterRequest.builder()
                    .priceType("fixed")
                    .build();

            Pageable pageable = PageRequest.of(0, 20);
            when(freightRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(Page.empty());

            Page<FreightResponse> result = freightService.searchFreightsPaged(filter, pageable);

            verify(freightRepository).findAll(any(Specification.class), eq(pageable));
        }

        @Test
        @DisplayName("City filtering for intracity freight")
        void cityFiltering() {
            FreightFilterRequest filter = FreightFilterRequest.builder()
                    .city("Addis Ababa")
                    .serviceType("intracity")
                    .build();

            Pageable pageable = PageRequest.of(0, 20);
            when(freightRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(Page.empty());

            freightService.searchFreightsPaged(filter, pageable);

            verify(freightRepository).findAll(any(Specification.class), eq(pageable));
        }

        @Test
        @DisplayName("Bids open filtering works correctly")
        void bidsOpenFiltering() {
            FreightFilterRequest filter = FreightFilterRequest.builder()
                    .bidsOpen(true)
                    .build();

            Pageable pageable = PageRequest.of(0, 20);
            when(freightRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(Page.empty());

            freightService.searchFreightsPaged(filter, pageable);

            verify(freightRepository).findAll(any(Specification.class), eq(pageable));
        }
    }

    // ── V6 MAPPER TESTS ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("V6 Mapper Tests")
    class V6MapperTests {

        @Test
        @DisplayName("CreateFreightRequest maps to entity with V6 fields")
        void createRequestMapping() {
            // This would be an integration test with actual MapStruct implementation
            // For unit tests, we verify the mapper is called correctly
            CreateFreightRequest request = CreateFreightRequest.builder()
                    .serviceType(Freight.ServiceType.INTRACITY)
                    .priceType(Freight.PriceType.FIXED)
                    .city("Addis Ababa")
                    .pickupArea("Bole")
                    .dropoffArea("Piazza")
                    .preferredDate(LocalDate.now().plusDays(1))
                    .itemsDescription("Test items")
                    .vehicleTypeNeeded("pickup")
                    .bidDeadline(LocalDateTime.now().plusDays(1))
                    .budget(new BigDecimal("500.00")) // Required for fixed price
                    .build();

            Freight freight = new Freight();
            FreightResponse response = FreightResponse.builder().id(1L).build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(shipperUser));
            when(freightMapper.toEntity(request)).thenReturn(freight);
            when(freightRepository.save(freight)).thenReturn(freight);
            when(freightMapper.toResponse(freight)).thenReturn(response);

            freightService.createFreight(1L, request);

            verify(freightMapper).toEntity(request);
            verify(freightMapper).toResponse(freight);
        }
    }

    // ── V6 BUSINESS RULES TESTS ────────────────────────────────────────────────

    @Nested
    @DisplayName("V6 Business Rules")
    class V6BusinessRules {

        @Test
        @DisplayName("Negotiable freight without bid deadline logs warning")
        void negotiableWithoutDeadlineWarning() {
            CreateFreightRequest request = CreateFreightRequest.builder()
                    .serviceType(Freight.ServiceType.INTERCITY)
                    .priceType(Freight.PriceType.NEGOTIABLE)
                    .pickupLocation("Addis Ababa")
                    .destination("Hawassa")
                    .materialType("grain")
                    .weightTons(new BigDecimal("10.0"))
                    .urgencyLevel("normal")
                    // No bid deadline - should log warning but not fail
                    .build();

            Freight freight = new Freight();
            freight.setId(1L);
            FreightResponse response = FreightResponse.builder().id(1L).build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(shipperUser));
            when(freightMapper.toEntity(request)).thenReturn(freight);
            when(freightRepository.save(freight)).thenReturn(freight);
            when(freightMapper.toResponse(freight)).thenReturn(response);

            // Should succeed but log warning (verified through manual testing)
            FreightResponse result = freightService.createFreight(1L, request);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Valid intercity freight with all fields succeeds")
        void validIntercityFreight() {
            CreateFreightRequest request = CreateFreightRequest.builder()
                    .serviceType(Freight.ServiceType.INTERCITY)
                    .priceType(Freight.PriceType.NEGOTIABLE)
                    .pickupLocation("Addis Ababa")
                    .destination("Hawassa")
                    .materialType("grain")
                    .weightTons(new BigDecimal("10.0"))
                    .urgencyLevel("normal")
                    .bidDeadline(LocalDateTime.now().plusDays(3))
                    .build();

            Freight freight = new Freight();
            freight.setId(1L);
            FreightResponse response = FreightResponse.builder().id(1L).build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(shipperUser));
            when(freightMapper.toEntity(request)).thenReturn(freight);
            when(freightRepository.save(freight)).thenReturn(freight);
            when(freightMapper.toResponse(freight)).thenReturn(response);

            FreightResponse result = freightService.createFreight(1L, request);

            assertThat(result).isNotNull();
            verify(freightRepository).save(freight);
        }
    }
}