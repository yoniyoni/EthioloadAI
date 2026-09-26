package com.ethioloadai.contract.service;

import com.ethioloadai.contract.dto.ContractResponse;
import com.ethioloadai.user.entity.User;

public interface ContractService {

    /**
     * Retrieves the aggregated agreement and contract status for a freight shipment.
     *
     * @param freightId ID of the freight shipment
     * @param caller Authenticated user attempting access
     * @return ContractResponse containing contract parties, financial terms, and execution state
     */
    ContractResponse getContractByFreightId(Long freightId, User caller);
}
