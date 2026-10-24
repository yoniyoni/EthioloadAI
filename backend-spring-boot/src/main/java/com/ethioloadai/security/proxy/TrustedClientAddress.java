package com.ethioloadai.security.proxy;

import jakarta.servlet.http.HttpServletRequest;

public final class TrustedClientAddress {
    private TrustedClientAddress() {}

    public static String resolve(HttpServletRequest request) {
        Object trustedAddress = request.getAttribute(OriginProxyGuardFilter.TRUSTED_CLIENT_IP_ATTRIBUTE);
        return trustedAddress instanceof String address ? address : request.getRemoteAddr();
    }
}
