package com.ethioloadai.carrier.company.repository;

import com.ethioloadai.carrier.company.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CarrierCompanyDocumentRepository extends JpaRepository<CarrierCompanyDocument, Long> {
    List<CarrierCompanyDocument> findByCarrierCompanyIdOrderById(Long companyId);
    Optional<CarrierCompanyDocument> findByCarrierCompanyIdAndDocumentType(Long companyId, CompanyDocumentType type);
    Optional<CarrierCompanyDocument> findByIdAndCarrierCompanyId(Long id, Long companyId);
}

