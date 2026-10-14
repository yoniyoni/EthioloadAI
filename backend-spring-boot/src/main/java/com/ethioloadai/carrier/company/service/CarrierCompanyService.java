package com.ethioloadai.carrier.company.service;

import com.ethioloadai.carrier.company.dto.*;
import com.ethioloadai.carrier.company.entity.*;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.document.storage.DocumentStorage.StoredDocument;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface CarrierCompanyService {
    CarrierCompanyResponse create(CarrierCompanyRequest request, User caller);
    Page<CarrierCompanyResponse> mine(User caller, int page, int size);
    CarrierCompanyResponse get(Long id, User caller);
    CarrierCompanyResponse update(Long id, CarrierCompanyRequest request, User caller);
    CarrierCompanyResponse submit(Long id, User caller);
    CarrierCompanyDocumentResponse upload(Long id, CompanyDocumentType type, MultipartFile file, User caller);
    List<CarrierCompanyDocumentResponse> documents(Long id, User caller);
    StoredDocument download(Long id, Long documentId, User caller);
    Page<CarrierCompanyResponse> applications(VerificationStatus status, int page, int size, User caller);
    CarrierCompanyResponse review(Long id, VerificationStatus decision, String message, User caller);
}

