package com.ethioloadai.carrier.company.repository;

import com.ethioloadai.carrier.company.entity.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface CarrierCompanyRepository extends JpaRepository<CarrierCompany, Long> {
    Page<CarrierCompany> findByOwnerId(Long ownerId, Pageable pageable);
    Page<CarrierCompany> findByVerificationStatus(VerificationStatus status, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CarrierCompany c where c.id = :id")
    Optional<CarrierCompany> findForUpdate(@Param("id") Long id);
    boolean existsByTinNumberAndIdNot(String tinNumber, Long id);
    boolean existsByBusinessRegistrationNumberAndIdNot(String number, Long id);
}

