package com.ethioloadai.carrier.team.service;

import com.ethioloadai.carrier.team.entity.CompanyRole;
import com.ethioloadai.carrier.company.repository.CarrierCompanyRepository;
import com.ethioloadai.carrier.company.entity.VerificationStatus;
import com.ethioloadai.carrier.team.entity.MembershipStatus;
import com.ethioloadai.carrier.team.exception.CompanyTeamException;
import com.ethioloadai.carrier.team.repository.CompanyMembershipRepository;
import com.ethioloadai.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 3 — Centralized company authorization service.
 *
 * <p>Designed for use in {@code @PreAuthorize} SpEL expressions as well as
 * direct service-layer calls.  Every method resolves authorization from the
 * live database, so membership deactivation or role changes take effect on the
 * very next request — no token invalidation required.
 *
 * <p>Example controller usage:
 * <pre>
 *   {@literal @}PreAuthorize("@companySecurity.isOwnerOf(#companyId, authentication)")
 *   public ResponseEntity<?> invite(@PathVariable Long companyId, ...) { ... }
 *
 *   {@literal @}PreAuthorize("@companySecurity.isMemberOf(#companyId, authentication)")
 *   public ResponseEntity<?> viewFleet(@PathVariable Long companyId, ...) { ... }
 * </pre>
 *
 * <p>All methods return {@code false} (not throw) when the caller does not have
 * the requested permission, allowing Spring Security to produce a consistent 403.
 * Methods that take a {@link User} principal directly throw
 * {@link CompanyTeamException} so service-layer callers get the right HTTP status.
 *
 * <h2>Role matrix</h2>
 * <pre>
 *   CARRIER_OWNER  — full company management (team, invitations, fleet view)
 *   FLEET_MANAGER  — fleet + driver + vehicle read/write within company
 *   DISPATCHER     — read-only access to company fleet resources
 *   ADMIN          — cross-company administrative access (separate path)
 * </pre>
 */
@Service("companySecurity")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanySecurityService {

    private final CompanyMembershipRepository memberships;
    private final CarrierCompanyRepository companies;

    // ── @PreAuthorize-compatible methods (take Authentication) ─────────────────

    /**
     * True when the authenticated caller is an ACTIVE CARRIER_OWNER of {@code companyId}.
     * Used for write operations: team management, invitations, company edits.
     */
    public boolean isOwnerOf(Long companyId, Authentication auth) {
        User caller = extractUser(auth);
        if (caller == null) return false;
        return hasActiveRole(companyId, caller.getId(), CompanyRole.CARRIER_OWNER);
    }

    /**
     * True when the authenticated caller has any ACTIVE membership in {@code companyId}.
     * Used for read operations: view company, list members, view fleet.
     */
    public boolean isMemberOf(Long companyId, Authentication auth) {
        User caller = extractUser(auth);
        if (caller == null) return false;
        return hasActiveMembership(companyId, caller.getId());
    }

    /**
     * True when caller is an ACTIVE FLEET_MANAGER or CARRIER_OWNER in {@code companyId}.
     * Used for fleet/driver/vehicle write operations.
     */
    public boolean canManageFleet(Long companyId, Authentication auth) {
        User caller = extractUser(auth);
        if (caller == null) return false;
        return hasActiveRole(companyId, caller.getId(), CompanyRole.FLEET_MANAGER)
                || hasActiveRole(companyId, caller.getId(), CompanyRole.CARRIER_OWNER);
    }

    /** Fleet writes require both a management membership and company approval. */
    public boolean canOperateFleet(Long companyId, Authentication auth) {
        return canManageFleet(companyId, auth) && companies.findById(companyId)
                .map(company -> company.getVerificationStatus() == VerificationStatus.APPROVED)
                .orElse(false);
    }

    /**
     * True when caller is an ACTIVE FLEET_MANAGER, DISPATCHER, or CARRIER_OWNER.
     * Used for fleet read operations.
     */
    public boolean canViewFleet(Long companyId, Authentication auth) {
        User caller = extractUser(auth);
        if (caller == null) return false;
        return hasActiveMembership(companyId, caller.getId());
    }

    /**
     * True when the caller is an ACTIVE member of {@code companyId} OR is a platform ADMIN.
     * Used for resources that admins legitimately need cross-company access to.
     */
    public boolean isMemberOrAdmin(Long companyId, Authentication auth) {
        User caller = extractUser(auth);
        if (caller == null) return false;
        if (caller.getRole() == User.Role.ADMIN) return true;
        return hasActiveMembership(companyId, caller.getId());
    }

    // ── Service-layer methods (take User, throw on denial) ────────────────────

    /**
     * Require the caller to be an ACTIVE CARRIER_OWNER of {@code companyId}.
     * Throws {@link CompanyTeamException} (403) on failure.
     */
    public void requireOwner(Long companyId, User caller) {
        if (!hasActiveRole(companyId, caller.getId(), CompanyRole.CARRIER_OWNER)) {
            throw CompanyTeamException.forbidden();
        }
    }

    /**
     * Require the caller to be an ACTIVE member of {@code companyId}.
     * Throws {@link CompanyTeamException} (403) on failure.
     */
    public void requireMember(Long companyId, User caller) {
        if (!hasActiveMembership(companyId, caller.getId())) {
            throw CompanyTeamException.forbidden();
        }
    }

    /**
     * Require the caller to be an ACTIVE FLEET_MANAGER or CARRIER_OWNER.
     * Throws {@link CompanyTeamException} (403) on failure.
     */
    public void requireFleetManager(Long companyId, User caller) {
        if (!hasActiveRole(companyId, caller.getId(), CompanyRole.FLEET_MANAGER)
                && !hasActiveRole(companyId, caller.getId(), CompanyRole.CARRIER_OWNER)) {
            throw CompanyTeamException.forbidden();
        }
    }

    /**
     * Require an ACTIVE CARRIER_OWNER or platform ADMIN.
     * Throws {@link CompanyTeamException} (403) on failure.
     */
    public void requireOwnerOrAdmin(Long companyId, User caller) {
        if (caller.getRole() == User.Role.ADMIN) return;
        requireOwner(companyId, caller);
    }

    /**
     * Require an ACTIVE company member or platform ADMIN.
     * Throws {@link CompanyTeamException} (403) on failure.
     */
    public void requireMemberOrAdmin(Long companyId, User caller) {
        if (caller.getRole() == User.Role.ADMIN) return;
        requireMember(companyId, caller);
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    /** Public programmatic check — returns true if userId is an active member of companyId. */
    public boolean isActiveMemberOf(Long userId, Long companyId) {
        if (userId == null || companyId == null) return false;
        return hasActiveMembership(companyId, userId);
    }

    private boolean hasActiveRole(Long companyId, Long userId, CompanyRole role) {
        return memberships.findByCarrierCompanyIdAndUserId(companyId, userId)
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .filter(m -> m.getRole() == role)
                .isPresent();
    }

    private boolean hasActiveMembership(Long companyId, Long userId) {
        return memberships.findByCarrierCompanyIdAndUserId(companyId, userId)
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .isPresent();
    }

    private User extractUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        // JwtAuthenticationFilter sets the principal to the raw User entity directly.
        if (principal instanceof User u) return u;
        // Defensive fallback for test contexts that use UserDetailsImpl as principal.
        if (principal instanceof com.ethioloadai.security.model.UserDetailsImpl ud) return ud.getUser();
        return null;
    }
}
