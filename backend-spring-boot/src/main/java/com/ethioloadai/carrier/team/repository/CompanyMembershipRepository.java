package com.ethioloadai.carrier.team.repository;
import com.ethioloadai.carrier.team.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import java.util.*;

public interface CompanyMembershipRepository extends JpaRepository<CompanyMembership, Long> {
 Optional<CompanyMembership> findByCarrierCompanyIdAndUserId(Long companyId, Long userId);
 Optional<CompanyMembership> findByIdAndCarrierCompanyId(Long id, Long companyId);
 Page<CompanyMembership> findByCarrierCompanyId(Long companyId, Pageable pageable);
 Page<CompanyMembership> findByUserIdAndStatus(Long userId, MembershipStatus status, Pageable pageable);
 /** Phase 3: all ACTIVE memberships for a user across all companies. */
 List<CompanyMembership> findByUserIdAndStatus(Long userId, MembershipStatus status);
 /** Phase 3: shortcut — all ACTIVE memberships for a user (non-paged). */
 @Query("select m from CompanyMembership m where m.userId = :userId and m.status = com.ethioloadai.carrier.team.entity.MembershipStatus.ACTIVE")
 List<CompanyMembership> findActiveByUserId(@Param("userId") Long userId);
 @Query("select count(m) > 0 from CompanyMembership m, User u where m.userId = u.id and m.carrierCompanyId = :companyId and m.status = com.ethioloadai.carrier.team.entity.MembershipStatus.ACTIVE and lower(trim(u.email)) = :email")
 boolean activeEmailExists(@Param("companyId") Long companyId, @Param("email") String email);
}

