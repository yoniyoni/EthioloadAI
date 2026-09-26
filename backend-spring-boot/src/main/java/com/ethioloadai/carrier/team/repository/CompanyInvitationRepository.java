package com.ethioloadai.carrier.team.repository;
import com.ethioloadai.carrier.team.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.time.LocalDateTime;
import java.util.*;

public interface CompanyInvitationRepository extends JpaRepository<CompanyInvitation, Long> {
 Optional<CompanyInvitation> findByTokenHash(String tokenHash);
 Optional<CompanyInvitation> findByIdAndCarrierCompanyId(Long id, Long companyId);
 List<CompanyInvitation> findByCarrierCompanyIdAndInvitedEmailAndStatus(Long companyId, String email, InvitationStatus status);
 Page<CompanyInvitation> findByCarrierCompanyId(Long companyId, Pageable pageable);
 boolean existsByCarrierCompanyIdAndCreatedAtAfter(Long companyId, LocalDateTime after);
 long countByCarrierCompanyIdAndCreatedAtAfter(Long companyId, LocalDateTime after);
}

