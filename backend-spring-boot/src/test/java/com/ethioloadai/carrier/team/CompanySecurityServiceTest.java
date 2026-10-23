package com.ethioloadai.carrier.team;

import com.ethioloadai.carrier.company.entity.CarrierCompany;
import com.ethioloadai.carrier.company.entity.VerificationStatus;
import com.ethioloadai.carrier.company.repository.CarrierCompanyRepository;
import com.ethioloadai.carrier.team.entity.CompanyMembership;
import com.ethioloadai.carrier.team.entity.CompanyRole;
import com.ethioloadai.carrier.team.entity.MembershipStatus;
import com.ethioloadai.carrier.team.repository.CompanyMembershipRepository;
import com.ethioloadai.carrier.team.service.CompanySecurityService;
import com.ethioloadai.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CompanySecurityServiceTest {
    private final CompanyMembershipRepository memberships = mock(CompanyMembershipRepository.class);
    private final CarrierCompanyRepository companies = mock(CarrierCompanyRepository.class);
    private final CompanySecurityService security = new CompanySecurityService(memberships, companies);

    @Test
    void commercialOffersAreOwnerAndDispatcherOnly() {
        User caller = User.builder().id(5L).build();
        Authentication auth = authentication(caller);

        assertThat(canNegotiateAs(caller, auth, CompanyRole.CARRIER_OWNER)).isTrue();
        assertThat(canNegotiateAs(caller, auth, CompanyRole.DISPATCHER)).isTrue();
        assertThat(canNegotiateAs(caller, auth, CompanyRole.FLEET_MANAGER)).isFalse();
    }

    @Test
    void dispatchAndAssignmentRolesStaySeparateFromFleetManagementRoles() {
        User caller = User.builder().id(5L).build();
        Authentication auth = authentication(caller);

        CarrierCompany approvedCompany = CarrierCompany.builder()
                .verificationStatus(VerificationStatus.APPROVED).build();
        when(memberships.findByCarrierCompanyIdAndUserId(10L, caller.getId()))
                .thenReturn(Optional.of(activeMembership(CompanyRole.FLEET_MANAGER)));
        when(companies.findById(10L)).thenReturn(Optional.of(approvedCompany));

        assertThat(security.canOperateDispatch(10L, auth)).isTrue();
        assertThat(security.canOperateAssignments(10L, auth)).isTrue();
        assertThat(security.canOperateFleet(10L, auth)).isTrue();

        when(memberships.findByCarrierCompanyIdAndUserId(10L, caller.getId()))
                .thenReturn(Optional.of(activeMembership(CompanyRole.CARRIER_OWNER)));
        assertThat(security.canOperateDispatch(10L, auth)).isTrue();
        assertThat(security.canOperateAssignments(10L, auth)).isTrue();

        when(memberships.findByCarrierCompanyIdAndUserId(10L, caller.getId()))
                .thenReturn(Optional.of(activeMembership(CompanyRole.DISPATCHER)));

        assertThat(security.canOperateDispatch(10L, auth)).isTrue();
        assertThat(security.canOperateAssignments(10L, auth)).isTrue();
        assertThat(security.canOperateFleet(10L, auth)).isFalse();

        approvedCompany.setVerificationStatus(VerificationStatus.PENDING);
        assertThat(security.canOperateAssignments(10L, auth)).isFalse();
    }

    private boolean canNegotiateAs(User caller, Authentication auth, CompanyRole role) {
        when(memberships.findByCarrierCompanyIdAndUserId(10L, caller.getId()))
                .thenReturn(Optional.of(activeMembership(role)));
        return security.canNegotiateOffers(10L, auth);
    }

    private Authentication authentication(User caller) {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(caller);
        return auth;
    }

    private CompanyMembership activeMembership(CompanyRole role) {
        CompanyMembership membership = new CompanyMembership();
        membership.setRole(role);
        membership.setStatus(MembershipStatus.ACTIVE);
        return membership;
    }
}
