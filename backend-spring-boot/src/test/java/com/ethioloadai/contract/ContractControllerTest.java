package com.ethioloadai.contract;

import com.ethioloadai.contract.controller.ContractController;
import com.ethioloadai.contract.dto.ContractFreightDto;
import com.ethioloadai.contract.dto.ContractPartyDto;
import com.ethioloadai.contract.dto.ContractResponse;
import com.ethioloadai.contract.service.ContractService;
import com.ethioloadai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContractController")
class ContractControllerTest {

    @Mock
    private ContractService contractService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private ContractController contractController;

    private User caller;

    @BeforeEach
    void setUp() {
        caller = User.builder()
                .id(1L)
                .fullName("Abebe Shipper")
                .role(User.Role.SHIPPER)
                .build();
    }

    @Test
    @DisplayName("GET /api/contracts/{freightId} returns 200 OK with ContractResponse")
    void getContract_returns200Ok() {
        when(authentication.getPrincipal()).thenReturn(caller);

        ContractResponse mockResponse = new ContractResponse(
                50L,
                10L,
                "active",
                "paid",
                new BigDecimal("28000.00"),
                LocalDate.of(2026, 9, 30),
                new ContractPartyDto("Abebe Shipper", "shipper@example.com"),
                new ContractPartyDto("Kebede Driver", "driver@example.com"),
                new ContractFreightDto("Addis Ababa, Bole", "Hawassa Industrial Park")
        );

        when(contractService.getContractByFreightId(10L, caller)).thenReturn(mockResponse);

        ResponseEntity<ContractResponse> responseEntity = contractController.getContract(10L, authentication);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().id()).isEqualTo(50L);
        assertThat(responseEntity.getBody().freightId()).isEqualTo(10L);
        assertThat(responseEntity.getBody().status()).isEqualTo("active");
        assertThat(responseEntity.getBody().paymentStatus()).isEqualTo("paid");
        assertThat(responseEntity.getBody().agreedPrice()).isEqualByComparingTo("28000.00");
        assertThat(responseEntity.getBody().deadline()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(responseEntity.getBody().shipper().name()).isEqualTo("Abebe Shipper");
        assertThat(responseEntity.getBody().driver().name()).isEqualTo("Kebede Driver");
        assertThat(responseEntity.getBody().freight().deliveryLocation()).isEqualTo("Hawassa Industrial Park");

        verify(contractService).getContractByFreightId(10L, caller);
    }
}
