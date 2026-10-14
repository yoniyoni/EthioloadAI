package com.ethioloadai.contract.controller;

import com.ethioloadai.contract.dto.ContractResponse;
import com.ethioloadai.contract.service.ContractService;
import com.ethioloadai.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/contracts")
@RequiredArgsConstructor
@Tag(name = "Contracts", description = "Endpoints for viewing freight agreements and contracts")
public class ContractController {

    private static final Logger log = LoggerFactory.getLogger(ContractController.class);

    private final ContractService contractService;

    @GetMapping("/{freightId}")
    @Operation(summary = "Get contract by freight ID",
               description = "Retrieves the aggregated agreement terms and contract status for an authorized freight shipment. " +
                             "Caller must be shipper, assigned driver, or admin.")
    public ResponseEntity<ContractResponse> getContract(
            @Parameter(description = "Freight shipment ID", required = true)
            @PathVariable Long freightId,
            Authentication authentication) {

        User caller = getUserFromAuthentication(authentication);
        log.info("GET /api/contracts/{} caller={}", freightId, caller.getId());

        ContractResponse response = contractService.getContractByFreightId(freightId, caller);
        return ResponseEntity.ok(response);
    }

    private User getUserFromAuthentication(Authentication authentication) {
        return (User) authentication.getPrincipal();
    }
}
