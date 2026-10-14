package com.ethioloadai.contract.exception;

import com.ethioloadai.exception.BaseException;

public class ContractException extends BaseException {

    public ContractException(String message, String code, int httpStatus) {
        super(message, code, httpStatus);
    }

    public static class ContractNotFoundException extends ContractException {
        public ContractNotFoundException(String message) {
            super(message, "CONTRACT_NOT_FOUND", 404);
        }
    }

    public static class UnauthorizedContractAccessException extends ContractException {
        public UnauthorizedContractAccessException(String message) {
            super(message, "FORBIDDEN", 403);
        }
    }
}
