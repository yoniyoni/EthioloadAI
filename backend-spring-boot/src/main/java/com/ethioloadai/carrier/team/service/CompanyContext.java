package com.ethioloadai.carrier.team.service;

import com.ethioloadai.carrier.company.entity.CarrierCompany;
import com.ethioloadai.carrier.team.entity.CompanyMembership;
import com.ethioloadai.carrier.team.entity.CompanyRole;
import com.ethioloadai.carrier.team.entity.MembershipStatus;

/**
 * Immutable snapshot of the authenticated user's resolved carrier-company context.
 *
 * <p>Produced by {@link CompanyContextService}. Every field is guaranteed non-null.
 *
 * <p>Phase 3 contract:
 * <ul>
 *   <li>Membership is always {@link MembershipStatus#ACTIVE}.</li>
 *   <li>The company reference is the authoritative tenant boundary for all
 *       company-scoped operations.</li>
 * </ul>
 */
public record CompanyContext(
        CompanyMembership membership,
        CarrierCompany company
) {

    /** Convenience — the resolved carrier company's primary key. */
    public Long companyId() {
        return company.getId();
    }

    /** Convenience — the resolved company role for the caller. */
    public CompanyRole role() {
        return membership.getRole();
    }

    /** True when the caller is the primary carrier owner of this company. */
    public boolean isOwner() {
        return membership.getRole() == CompanyRole.CARRIER_OWNER;
    }

    /** True when the caller has FLEET_MANAGER privileges in this company. */
    public boolean isFleetManager() {
        return membership.getRole() == CompanyRole.FLEET_MANAGER;
    }

    /** True when the caller is a DISPATCHER in this company. */
    public boolean isDispatcher() {
        return membership.getRole() == CompanyRole.DISPATCHER;
    }

    /**
     * True when the caller belongs to the given company.
     * Prevents cross-company ID substitution attacks.
     */
    public boolean belongsTo(Long otherCompanyId) {
        return companyId().equals(otherCompanyId);
    }
}
