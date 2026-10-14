package com.ethioloadai.carrier.company.exception;

import com.ethioloadai.exception.BaseException;

public class CarrierCompanyException extends BaseException {
    public CarrierCompanyException(String message, String code, int status) {
        super(message, code, status);
    }
    public static CarrierCompanyException invalid(String message) {
        return new CarrierCompanyException(message, "INVALID_CARRIER_APPLICATION", 400);
    }
    public static CarrierCompanyException missing() {
        return new CarrierCompanyException("Carrier company or document not found", "CARRIER_NOT_FOUND", 404);
    }
    public static CarrierCompanyException duplicate() {
        return new CarrierCompanyException("TIN or business registration number is already registered",
            "DUPLICATE_CARRIER_REGISTRATION", 409);
    }
}

