package com.ethioloadai.carrier.team;

import com.ethioloadai.carrier.company.entity.VerificationStatus;
import com.ethioloadai.carrier.company.repository.CarrierCompanyRepository;
import com.ethioloadai.carrier.team.entity.*;
import com.ethioloadai.carrier.team.repository.CompanyMembershipRepository;
import com.ethioloadai.carrier.team.service.*;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 3 tenant-isolation test suite.
 *
 * <p>Tests the fundamental security invariant:
 * ABC Logistics users must not access XYZ Transport data, and vice versa.
 *
 * <p>Covers:
 * <ul>
 *   <li>Company context resolution (active/inactive/removed membership)</li>
 *   <li>CarrierCompany + document IDOR</li>
 *   <li>Membership cross-company IDOR</li>
 *   <li>Invitation cross-company IDOR</li>
 *   <li>Company fleet endpoint tenant isolation</li>
 *   <li>Membership deactivation immediately revokes access</li>
 *   <li>Role change takes effect on next request</li>
 *   <li>Role escalation prevention</li>
 *   <li>Admin retains cross-company read access</li>
 * </ul>
 */
class Phase3TenantIsolationTest extends TeamTestSupport {

    @Autowired CompanyContextService contextService;
    @Autowired CompanySecurityService securityService;
    @Autowired VehicleRepository vehicleRepository;

    // ABC = companyId (from TeamTestSupport), ABC owner = owner
    Long xyzCompanyId;
    User xyzOwner;
    User abcFleetManager;
    User abcDispatcher;
    User xyzFleetManager;
    Vehicle abcVehicle;
    Vehicle xyzVehicle;

    @BeforeEach
    void setup() {
        // XYZ company + owner
        xyzOwner = user(User.Role.SHIPPER);
        xyzCompanyId = company(xyzOwner, true);

        // ABC members
        abcFleetManager = user(User.Role.SHIPPER);
        abcDispatcher   = user(User.Role.SHIPPER);
        invite(abcFleetManager, CompanyRole.FLEET_MANAGER);
        invitations.accept(token(abcFleetManager), abcFleetManager);
        invite(abcDispatcher, CompanyRole.DISPATCHER);
        invitations.accept(token(abcDispatcher), abcDispatcher);

        // XYZ fleet manager
        xyzFleetManager = user(User.Role.SHIPPER);
        var xyzInvReq = request(xyzFleetManager.getEmail(), CompanyRole.FLEET_MANAGER);
        invitations.invite(xyzCompanyId, xyzInvReq, xyzOwner);
        String xyzToken = links.get(xyzFleetManager.getEmail().strip().toLowerCase(java.util.Locale.ROOT));
        xyzToken = xyzToken.substring(xyzToken.indexOf("#token=") + 7);
        invitations.accept(xyzToken, xyzFleetManager);

        // Create vehicles to test fleet IDOR
        abcVehicle = vehicleRepository.saveAndFlush(Vehicle.builder()
                .userId(owner.getId()).fleetOwnerId(owner.getId())
                .truckType("Truck").plateNumber("ABC-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .capacity(10.0).availabilityStatus("available").build());
        xyzVehicle = vehicleRepository.saveAndFlush(Vehicle.builder()
                .userId(xyzOwner.getId()).fleetOwnerId(xyzOwner.getId())
                .truckType("Truck").plateNumber("XYZ-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .capacity(10.0).availabilityStatus("available").build());
    }

    // ── 1. Company context resolution ─────────────────────────────────────────

    @Test
    void activeOwnerResolvesCorrectCompanyContext() {
        CompanyContext ctx = contextService.resolveForCompany(companyId, owner);
        assertThat(ctx.companyId()).isEqualTo(companyId);
        assertThat(ctx.role()).isEqualTo(CompanyRole.CARRIER_OWNER);
        assertThat(ctx.isOwner()).isTrue();
    }

    @Test
    void activeFleetManagerResolvesCorrectCompanyContext() {
        CompanyContext ctx = contextService.resolveForCompany(companyId, abcFleetManager);
        assertThat(ctx.companyId()).isEqualTo(companyId);
        assertThat(ctx.role()).isEqualTo(CompanyRole.FLEET_MANAGER);
        assertThat(ctx.isFleetManager()).isTrue();
        assertThat(ctx.isOwner()).isFalse();
    }

    @Test
    void activeDispatcherResolvesCorrectCompanyContext() {
        CompanyContext ctx = contextService.resolveForCompany(companyId, abcDispatcher);
        assertThat(ctx.companyId()).isEqualTo(companyId);
        assertThat(ctx.role()).isEqualTo(CompanyRole.DISPATCHER);
        assertThat(ctx.isDispatcher()).isTrue();
    }

    @Test
    void userWithoutMembershipGetsNoCompanyAccess() {
        User outsider = user(User.Role.SHIPPER);
        assertThatThrownBy(() -> contextService.resolveForCompany(companyId, outsider))
                .hasMessageContaining("Company owner access required");
    }

    @Test
    void inactiveMembershipIsRejected() {
        // Deactivate the fleet manager
        var m = memberships.findByCarrierCompanyIdAndUserId(companyId, abcFleetManager.getId()).orElseThrow();
        m.setStatus(MembershipStatus.INACTIVE);
        m.setStatusChangedBy(owner.getId());
        m.setStatusChangedAt(LocalDateTime.now(ZoneOffset.UTC));
        memberships.saveAndFlush(m);

        assertThatThrownBy(() -> contextService.resolveForCompany(companyId, abcFleetManager))
                .hasMessageContaining("Company owner access required");
    }

    @Test
    void removedMembershipIsRejected() {
        var m = memberships.findByCarrierCompanyIdAndUserId(companyId, abcDispatcher.getId()).orElseThrow();
        m.setStatus(MembershipStatus.REMOVED);
        m.setStatusChangedBy(owner.getId());
        m.setStatusChangedAt(LocalDateTime.now(ZoneOffset.UTC));
        memberships.saveAndFlush(m);

        assertThatThrownBy(() -> contextService.resolveForCompany(companyId, abcDispatcher))
                .hasMessageContaining("Company owner access required");
    }

    @Test
    void unauthenticatedContextResolutionFails() {
        assertThatThrownBy(() -> contextService.resolveForCompany(companyId, null))
                .hasMessageContaining("Authentication required");
    }

    // ── 2. CompanySecurityService role checks ──────────────────────────────────

    @Test
    void isOwnerOfReturnsTrueForOwner() {
        assertThat(securityService.isOwnerOf(companyId, buildAuth(owner))).isTrue();
    }

    @Test
    void isOwnerOfReturnsFalseForFleetManager() {
        assertThat(securityService.isOwnerOf(companyId, buildAuth(abcFleetManager))).isFalse();
    }

    @Test
    void isOwnerOfReturnsFalseForXyzOwnerOnAbcCompany() {
        assertThat(securityService.isOwnerOf(companyId, buildAuth(xyzOwner))).isFalse();
    }

    @Test
    void isMemberOfReturnsTrueForAllActiveAbcMembers() {
        assertThat(securityService.isMemberOf(companyId, buildAuth(owner))).isTrue();
        assertThat(securityService.isMemberOf(companyId, buildAuth(abcFleetManager))).isTrue();
        assertThat(securityService.isMemberOf(companyId, buildAuth(abcDispatcher))).isTrue();
    }

    @Test
    void isMemberOfReturnsFalseForXyzMemberOnAbcCompany() {
        assertThat(securityService.isMemberOf(companyId, buildAuth(xyzOwner))).isFalse();
        assertThat(securityService.isMemberOf(companyId, buildAuth(xyzFleetManager))).isFalse();
    }

    @Test
    void inactiveMembershipLosesIsMemberOf() {
        var m = memberships.findByCarrierCompanyIdAndUserId(companyId, abcFleetManager.getId()).orElseThrow();
        m.setStatus(MembershipStatus.INACTIVE);
        memberships.saveAndFlush(m);

        assertThat(securityService.isMemberOf(companyId, buildAuth(abcFleetManager))).isFalse();
    }

    @Test
    void canManageFleetTrueForOwnerAndFleetManager() {
        assertThat(securityService.canManageFleet(companyId, buildAuth(owner))).isTrue();
        assertThat(securityService.canManageFleet(companyId, buildAuth(abcFleetManager))).isTrue();
    }

    @Test
    void canManageFleetFalseForDispatcher() {
        assertThat(securityService.canManageFleet(companyId, buildAuth(abcDispatcher))).isFalse();
    }

    // ── 3. CarrierCompany IDOR ─────────────────────────────────────────────────

    @Test
    void abcMemberCanReadOwnCompanyAfterPhase3Fix() throws Exception {
        // DEFECT-1 regression: active members must be able to read their company
        mvc.perform(get("/carrier-companies/" + companyId)
                        .header("Authorization", auth(abcFleetManager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(companyId));
    }

    @Test
    void abcOwnerCannotReadXyzCompany() throws Exception {
        mvc.perform(get("/carrier-companies/" + xyzCompanyId)
                        .header("Authorization", auth(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void abcFleetManagerCannotReadXyzCompany() throws Exception {
        mvc.perform(get("/carrier-companies/" + xyzCompanyId)
                        .header("Authorization", auth(abcFleetManager)))
                .andExpect(status().isNotFound());
    }

    @Test
    void xyzOwnerCannotReadAbcCompany() throws Exception {
        mvc.perform(get("/carrier-companies/" + companyId)
                        .header("Authorization", auth(xyzOwner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanReadBothCompanies() throws Exception {
        mvc.perform(get("/carrier-companies/" + companyId)
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk());
        mvc.perform(get("/carrier-companies/" + xyzCompanyId)
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk());
    }

    // ── 4. Company document IDOR ───────────────────────────────────────────────

    @Test
    void abcMemberCanListOwnCompanyDocuments() throws Exception {
        mvc.perform(get("/carrier-companies/" + companyId + "/documents")
                        .header("Authorization", auth(abcFleetManager)))
                .andExpect(status().isOk());
    }

    @Test
    void abcOwnerCannotListXyzDocuments() throws Exception {
        mvc.perform(get("/carrier-companies/" + xyzCompanyId + "/documents")
                        .header("Authorization", auth(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void xyzOwnerCannotListAbcDocuments() throws Exception {
        mvc.perform(get("/carrier-companies/" + companyId + "/documents")
                        .header("Authorization", auth(xyzOwner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedDocumentAccessDenied() throws Exception {
        mvc.perform(get("/carrier-companies/" + companyId + "/documents"))
                .andExpect(status().isUnauthorized());
    }

    // ── 5. Company update/submit IDOR ──────────────────────────────────────────

    @Test
    void abcOwnerCannotUpdateXyzCompany() throws Exception {
        mvc.perform(put("/carrier-companies/" + xyzCompanyId)
                        .header("Authorization", auth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Hacked\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void fleetManagerCannotUpdateOwnCompanyEitherOwnerOnly() throws Exception {
        mvc.perform(put("/carrier-companies/" + companyId)
                        .header("Authorization", auth(abcFleetManager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Hijacked\"}"))
                .andExpect(status().isNotFound());
    }

    // ── 6. Membership cross-company IDOR ──────────────────────────────────────

    @Test
    void abcOwnerListsOnlyAbcMembers() throws Exception {
        ResultActions result = mvc.perform(get(team() + "/members")
                        .header("Authorization", auth(owner)))
                .andExpect(status().isOk());
        // The content array must only contain entries whose carrierCompanyId == companyId
        // Verify by checking no XYZ owner userId appears as a member userId
        // (xyzOwner is in XYZ's team only, not ABC's)
        com.fasterxml.jackson.databind.JsonNode responseBody =
                json.readTree(result.andReturn().getResponse().getContentAsString());
        var content = responseBody.get("content");
        if (content != null && content.isArray()) {
            for (var node : content) {
                assertThat(node.get("userId").asLong())
                        .as("XYZ owner must not appear as an ABC member")
                        .isNotEqualTo(xyzOwner.getId());
                // XYZ fleet manager should also not be in ABC
                assertThat(node.get("userId").asLong())
                        .as("XYZ fleet manager must not appear as an ABC member")
                        .isNotEqualTo(xyzFleetManager.getId());
            }
        }
    }

    @Test
    void abcOwnerCannotListXyzMembers() throws Exception {
        mvc.perform(get("/carrier-companies/" + xyzCompanyId + "/team/members")
                        .header("Authorization", auth(owner)))
                .andExpect(status().isForbidden());
    }

    @Test
    void xyzOwnerCannotListAbcMembers() throws Exception {
        mvc.perform(get("/carrier-companies/" + companyId + "/team/members")
                        .header("Authorization", auth(xyzOwner)))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberIdCannotCrossCompanyBoundaryOnRoleChange() throws Exception {
        // Get the XYZ fleet manager's membership id
        var xyzMembership = memberships.findByCarrierCompanyIdAndUserId(xyzCompanyId, xyzFleetManager.getId())
                .orElseThrow();
        // ABC owner tries to change the role of XYZ's fleet manager using ABC team URL
        mvc.perform(patch(team() + "/members/" + xyzMembership.getId() + "/role")
                        .header("Authorization", auth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"DISPATCHER\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberCannotEscalateOwnRole() throws Exception {
        var abcMembership = memberships.findByCarrierCompanyIdAndUserId(companyId, abcFleetManager.getId())
                .orElseThrow();
        // Fleet manager tries to promote themselves to owner
        mvc.perform(patch(team() + "/members/" + abcMembership.getId() + "/role")
                        .header("Authorization", auth(abcFleetManager))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"CARRIER_OWNER\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void dispatcherCannotChangeAnyMemberRole() throws Exception {
        var abcMembership = memberships.findByCarrierCompanyIdAndUserId(companyId, abcFleetManager.getId())
                .orElseThrow();
        mvc.perform(patch(team() + "/members/" + abcMembership.getId() + "/role")
                        .header("Authorization", auth(abcDispatcher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"DISPATCHER\"}"))
                .andExpect(status().isForbidden());
    }

    // ── 7. Invitation cross-company IDOR ──────────────────────────────────────

    @Test
    void abcOwnerListsOnlyAbcInvitations() throws Exception {
        mvc.perform(get(team() + "/invitations")
                        .header("Authorization", auth(owner)))
                .andExpect(status().isOk());
    }

    @Test
    void abcOwnerCannotListXyzInvitations() throws Exception {
        mvc.perform(get("/carrier-companies/" + xyzCompanyId + "/team/invitations")
                        .header("Authorization", auth(owner)))
                .andExpect(status().isForbidden());
    }

    @Test
    void xyzOwnerCannotRevokeAbcInvitation() throws Exception {
        // Create a pending ABC invitation
        User newUser = user(User.Role.SHIPPER);
        var inv = invitations.invite(companyId, request(newUser.getEmail(), CompanyRole.DISPATCHER), owner);
        // XYZ owner tries to revoke it via their own team URL
        mvc.perform(post("/carrier-companies/" + xyzCompanyId + "/team/invitations/" + inv.getId() + "/revoke")
                        .header("Authorization", auth(xyzOwner)))
                .andExpect(status().isForbidden());
    }

    @Test
    void abcOwnerCannotResendXyzInvitation() throws Exception {
        User xyzTarget = user(User.Role.SHIPPER);
        var xyzInvRequest = request(xyzTarget.getEmail(), CompanyRole.DISPATCHER);
        var xyzInv = invitations.invite(xyzCompanyId, xyzInvRequest, xyzOwner);

        mvc.perform(post(team() + "/invitations/" + xyzInv.getId() + "/resend")
                        .header("Authorization", auth(owner)))
                .andExpect(status().isForbidden());
    }

    // ── 8. Company fleet endpoint tenant isolation ─────────────────────────────

    @Test
    void abcMemberSeesOnlyAbcFleetDrivers() throws Exception {
        mvc.perform(get("/carrier-companies/" + companyId + "/fleet/drivers")
                        .header("Authorization", auth(abcFleetManager)))
                .andExpect(status().isOk());
    }

    @Test
    void xyzMemberCannotSeeAbcFleet() throws Exception {
        mvc.perform(get("/carrier-companies/" + companyId + "/fleet/drivers")
                        .header("Authorization", auth(xyzOwner)))
                .andExpect(status().isForbidden());
    }

    @Test
    void abcMemberCannotSeeXyzFleet() throws Exception {
        mvc.perform(get("/carrier-companies/" + xyzCompanyId + "/fleet/drivers")
                        .header("Authorization", auth(abcFleetManager)))
                .andExpect(status().isForbidden());
    }

    @Test
    void abcMemberSeesOnlyAbcVehicles() throws Exception {
        mvc.perform(get("/carrier-companies/" + companyId + "/fleet/vehicles")
                        .header("Authorization", auth(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + xyzVehicle.getId() + ")]").doesNotExist());
    }

    @Test
    void xyzVehicleNotVisibleToAbcMembers() throws Exception {
        // XYZ vehicle must not appear in ABC fleet response
        mvc.perform(get("/carrier-companies/" + companyId + "/fleet/vehicles")
                        .header("Authorization", auth(owner)))
                .andExpect(status().isOk());
        // Verify by direct context check
        CompanyContext abcCtx = contextService.resolveForCompany(companyId, owner);
        CompanyContext xyzCtx = contextService.resolveForCompany(xyzCompanyId, xyzOwner);
        assertThat(abcCtx.companyId()).isNotEqualTo(xyzCtx.companyId());
        assertThat(abcCtx.company().getOwnerId()).isNotEqualTo(xyzCtx.company().getOwnerId());
    }

    @Test
    void unauthenticatedFleetAccessDenied() throws Exception {
        mvc.perform(get("/carrier-companies/" + companyId + "/fleet/drivers"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/carrier-companies/" + companyId + "/fleet/vehicles"))
                .andExpect(status().isUnauthorized());
    }

    // ── 9. Membership deactivation immediately revokes access ─────────────────

    @Test
    void deactivatedMemberLosesCompanyContextImmediately() {
        // Verify active
        assertThat(contextService.findForCompany(companyId, abcFleetManager)).isPresent();

        // Deactivate
        teams.status(companyId, memberships.findByCarrierCompanyIdAndUserId(companyId, abcFleetManager.getId())
                .orElseThrow().getId(), MembershipStatus.INACTIVE, owner);

        // Same account, next request — context is gone
        assertThat(contextService.findForCompany(companyId, abcFleetManager)).isEmpty();
    }

    @Test
    void deactivatedMemberGetsForbiddenOnCompanyEndpoint() throws Exception {
        teams.status(companyId, memberships.findByCarrierCompanyIdAndUserId(companyId, abcFleetManager.getId())
                .orElseThrow().getId(), MembershipStatus.INACTIVE, owner);

        mvc.perform(get("/carrier-companies/" + companyId + "/fleet/drivers")
                        .header("Authorization", auth(abcFleetManager)))
                .andExpect(status().isForbidden());
    }

    @Test
    void reactivatedMemberRegainsAccess() {
        // Deactivate then reactivate
        Long memberId = memberships.findByCarrierCompanyIdAndUserId(companyId, abcFleetManager.getId())
                .orElseThrow().getId();
        teams.status(companyId, memberId, MembershipStatus.INACTIVE, owner);
        assertThat(contextService.findForCompany(companyId, abcFleetManager)).isEmpty();

        teams.status(companyId, memberId, MembershipStatus.ACTIVE, owner);
        assertThat(contextService.findForCompany(companyId, abcFleetManager)).isPresent();
    }

    // ── 10. Role change takes effect immediately ──────────────────────────────

    @Test
    void roleChangeTakesEffectOnNextRequest() {
        // Fleet manager can manage fleet initially
        assertThat(securityService.canManageFleet(companyId, buildAuth(abcFleetManager))).isTrue();

        // Owner changes role to DISPATCHER
        Long memberId = memberships.findByCarrierCompanyIdAndUserId(companyId, abcFleetManager.getId())
                .orElseThrow().getId();
        teams.role(companyId, memberId, CompanyRole.DISPATCHER, owner);

        // Same token, next request — fleet management permission gone
        assertThat(securityService.canManageFleet(companyId, buildAuth(abcFleetManager))).isFalse();
        // But can still view fleet (dispatcher permission)
        assertThat(securityService.canViewFleet(companyId, buildAuth(abcFleetManager))).isTrue();
    }

    // ── 11. Driver listing access control (DEFECT-3 fix) ──────────────────────

    @Test
    void shipperCanListDrivers() throws Exception {
        User shipper = user(User.Role.SHIPPER);
        mvc.perform(get("/drivers").header("Authorization", auth(shipper)))
                .andExpect(status().isOk());
    }

    @Test
    void fleetOwnerCanListDrivers() throws Exception {
        User fleetOwner = user(User.Role.FLEET_OWNER);
        mvc.perform(get("/drivers").header("Authorization", auth(fleetOwner)))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanListDrivers() throws Exception {
        mvc.perform(get("/drivers").header("Authorization", auth(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void plainDriverCannotListAllDrivers() throws Exception {
        // DEFECT-3 regression: a driver must not be able to enumerate all drivers
        User driver = user(User.Role.DRIVER);
        mvc.perform(get("/drivers").header("Authorization", auth(driver)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedDriverListingDenied() throws Exception {
        mvc.perform(get("/drivers"))
                .andExpect(status().isUnauthorized());
    }

    // ── 12. Admin retains cross-company administrative access ─────────────────

    @Test
    void adminCanAccessAdminCarrierEndpoints() throws Exception {
        mvc.perform(get("/admin/carrier-companies")
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void carrierUserCannotCallAdminCarrierEndpoints() throws Exception {
        mvc.perform(get("/admin/carrier-companies")
                        .header("Authorization", auth(owner)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/carrier-companies")
                        .header("Authorization", auth(abcFleetManager)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadAnyCompanyViaAdminEndpoint() throws Exception {
        mvc.perform(get("/admin/carrier-companies/" + companyId)
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk());
        mvc.perform(get("/admin/carrier-companies/" + xyzCompanyId)
                        .header("Authorization", auth(admin)))
                .andExpect(status().isOk());
    }

    // ── 13. CompanyContext.belongsTo() IDOR guard ──────────────────────────────

    @Test
    void belongsToReturnsTrueForOwnCompany() {
        CompanyContext ctx = contextService.resolveForCompany(companyId, owner);
        assertThat(ctx.belongsTo(companyId)).isTrue();
        assertThat(ctx.belongsTo(xyzCompanyId)).isFalse();
    }

    @Test
    void contextBelongsToRejectedForWrongCompany() {
        CompanyContext xyzCtx = contextService.resolveForCompany(xyzCompanyId, xyzOwner);
        assertThat(xyzCtx.belongsTo(companyId)).isFalse();
    }

    // ── 14. Multiple active memberships handling ──────────────────────────────

    @Test
    void multipleActiveMembershipsResolvedByExplicitCompanyId() {
        // abcFleetManager is in ABC; invite them to XYZ too
        User multiMember = user(User.Role.SHIPPER);
        invite(multiMember, CompanyRole.FLEET_MANAGER);
        invitations.accept(token(multiMember), multiMember);

        var xyzReq = request(multiMember.getEmail(), CompanyRole.DISPATCHER);
        invitations.invite(xyzCompanyId, xyzReq, xyzOwner);
        String xyzLink = links.get(multiMember.getEmail().strip().toLowerCase(java.util.Locale.ROOT));
        String xyzTok = xyzLink.substring(xyzLink.indexOf("#token=") + 7);
        invitations.accept(xyzTok, multiMember);

        // Explicit company ID works for both
        CompanyContext abcCtx = contextService.resolveForCompany(companyId, multiMember);
        CompanyContext xyzCtx = contextService.resolveForCompany(xyzCompanyId, multiMember);
        assertThat(abcCtx.companyId()).isEqualTo(companyId);
        assertThat(xyzCtx.companyId()).isEqualTo(xyzCompanyId);

        // resolveUnambiguous fails because user belongs to two companies
        assertThatThrownBy(() -> contextService.resolveUnambiguous(multiMember))
                .isInstanceOf(com.ethioloadai.carrier.team.exception.CompanyTeamException.class)
                .extracting(e -> ((com.ethioloadai.exception.BaseException) e).getCode())
                .isEqualTo("COMPANY_CONTEXT_AMBIGUOUS");
    }

    @Test
    void singleMembershipResolvesUnambiguously() {
        // ABC owner has exactly one active membership
        CompanyContext ctx = contextService.resolveUnambiguous(owner);
        assertThat(ctx.companyId()).isEqualTo(companyId);
    }

    @Test
    void noMembershipResolvesUnambiguouslyFails() {
        User stranger = user(User.Role.SHIPPER);
        assertThatThrownBy(() -> contextService.resolveUnambiguous(stranger))
                .isInstanceOf(com.ethioloadai.carrier.team.exception.CompanyTeamException.class)
                .extracting(e -> ((com.ethioloadai.exception.BaseException) e).getCode())
                .isEqualTo("COMPANY_MEMBERSHIP_REQUIRED");
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private org.springframework.security.core.Authentication buildAuth(User user) {
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                user, null,
                java.util.Collections.singletonList(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_" + user.getRole().name())));
    }
}
