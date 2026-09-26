package com.ethioloadai.carrier.team.service;

import com.ethioloadai.carrier.company.entity.CarrierCompany;
import com.ethioloadai.carrier.company.repository.CarrierCompanyRepository;
import com.ethioloadai.carrier.team.entity.CompanyMembership;
import com.ethioloadai.carrier.team.entity.MembershipStatus;
import com.ethioloadai.carrier.team.exception.CompanyTeamException;
import com.ethioloadai.carrier.team.repository.CompanyMembershipRepository;
import com.ethioloadai.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Phase 3 — Centralized carrier-company context resolution.
 *
 * <p>Answers the canonical question: <em>"Which carrier company is this authenticated
 * user currently acting for, and what is their role?"</em>
 *
 * <p>Resolution chain:
 * <ol>
 *   <li>Authenticated {@link User} (from JWT → Spring Security context)</li>
 *   <li>→ Active {@link CompanyMembership} for that user</li>
 *   <li>→ {@link CarrierCompany}</li>
 *   <li>→ {@link CompanyContext} (immutable snapshot)</li>
 * </ol>
 *
 * <p><strong>Membership policy:</strong>
 * Only {@link MembershipStatus#ACTIVE} memberships grant company-scoped access.
 * INACTIVE and REMOVED memberships are silently excluded. This means deactivating
 * a member immediately revokes their company access on the next request — no token
 * invalidation or cache flush required.
 *
 * <p><strong>Multiple memberships:</strong>
 * The current business model allows a user to belong to multiple companies.
 * When a user has exactly one active membership the context is unambiguous.
 * When multiple active memberships exist, callers supply an explicit {@code companyId}
 * and this service validates that the caller is an active member of that company.
 * This avoids silently picking one company while still supporting multi-company users.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyContextService {

    private final CompanyMembershipRepository memberships;
    private final CarrierCompanyRepository companies;

    // ── Primary resolution methods ────────────────────────────────────────────

    /**
     * Resolve context from an explicit company ID supplied in the URL path.
     * Validates that the caller is an active member of that specific company.
     * Returns 404 (not 403) on failure to avoid leaking company existence.
     */
    public CompanyContext resolveForCompany(Long companyId, User caller) {
        requireAuthenticated(caller);
        CompanyMembership membership = memberships
                .findByCarrierCompanyIdAndUserId(companyId, caller.getId())
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .orElseThrow(CompanyTeamException::forbidden);
        CarrierCompany company = companies.findById(companyId)
                .orElseThrow(CompanyTeamException::forbidden);
        return new CompanyContext(membership, company);
    }

    /**
     * Resolve context when the caller has exactly one active membership.
     * Throws {@link CompanyTeamException} with COMPANY_MEMBERSHIP_REQUIRED
     * if the caller has no active membership, or COMPANY_CONTEXT_AMBIGUOUS
     * if they belong to multiple companies (caller must supply an explicit ID).
     */
    public CompanyContext resolveUnambiguous(User caller) {
        requireAuthenticated(caller);
        var active = memberships.findActiveByUserId(caller.getId());
        if (active.isEmpty()) {
            throw new CompanyTeamException(
                    "Active company membership required",
                    "COMPANY_MEMBERSHIP_REQUIRED", 403);
        }
        if (active.size() > 1) {
            throw new CompanyTeamException(
                    "Multiple company memberships exist — supply an explicit company ID",
                    "COMPANY_CONTEXT_AMBIGUOUS", 400);
        }
        CompanyMembership membership = active.get(0);
        CarrierCompany company = companies.findById(membership.getCarrierCompanyId())
                .orElseThrow(CompanyTeamException::forbidden);
        return new CompanyContext(membership, company);
    }

    /**
     * Return the caller's active membership for a given company, or empty.
     * Never throws — callers decide how to handle absence.
     */
    public Optional<CompanyContext> findForCompany(Long companyId, User caller) {
        if (caller == null || caller.getId() == null) return Optional.empty();
        return memberships
                .findByCarrierCompanyIdAndUserId(companyId, caller.getId())
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .flatMap(m -> companies.findById(companyId)
                        .map(c -> new CompanyContext(m, c)));
    }

    /**
     * True when the caller is an active member of the given company.
     * Used by {@link CompanySecurityService} for SpEL expressions.
     */
    public boolean isActiveMember(Long companyId, User caller) {
        return findForCompany(companyId, caller).isPresent();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void requireAuthenticated(User caller) {
        if (caller == null || caller.getId() == null) {
            throw new CompanyTeamException("Authentication required", "UNAUTHENTICATED", 401);
        }
    }
}
