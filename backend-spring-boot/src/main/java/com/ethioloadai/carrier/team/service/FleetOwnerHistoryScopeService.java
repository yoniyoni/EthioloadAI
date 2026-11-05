package com.ethioloadai.carrier.team.service;

import com.ethioloadai.carrier.team.repository.CompanyMembershipRepository;
import com.ethioloadai.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FleetOwnerHistoryScopeService {

    private final CompanyMembershipRepository memberships;

    public List<Long> getApprovedCompanyIds(User user) {
        if (user == null || user.getRole() != User.Role.FLEET_OWNER) {
            return List.of();
        }
        return memberships.findApprovedCompanyIdsByActiveMembershipUserId(user.getId());
    }
}
