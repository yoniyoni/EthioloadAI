package com.ethioloadai.carrier.company.service;

import com.ethioloadai.carrier.company.dto.*;
import com.ethioloadai.carrier.company.entity.*;
import com.ethioloadai.carrier.company.exception.CarrierCompanyException;
import com.ethioloadai.carrier.company.mapper.CarrierCompanyMapper;
import com.ethioloadai.carrier.company.repository.*;
import com.ethioloadai.document.storage.DocumentStorage;
import com.ethioloadai.exception.AuthorizationException;
import com.ethioloadai.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

import static com.ethioloadai.carrier.company.entity.VerificationStatus.*;
import static com.ethioloadai.carrier.company.exception.CarrierCompanyException.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CarrierCompanyServiceImpl implements CarrierCompanyService {
    private static final Set<CompanyDocumentType> REQUIRED_DOCUMENTS = Set.of(
        CompanyDocumentType.BUSINESS_LICENSE, CompanyDocumentType.BUSINESS_REGISTRATION,
        CompanyDocumentType.TIN_CERTIFICATE);
    private static final Map<String, String> FILE_TYPES = Map.of(
        "application/pdf", ".pdf", "image/jpeg", ".jpg", "image/png", ".png");
    private final CarrierCompanyRepository companies;
    private final CarrierCompanyDocumentRepository documentRepository;
    private final CarrierCompanyMapper mapper;
    private final DocumentStorage storage;
    private final com.ethioloadai.carrier.team.repository.CompanyMembershipRepository memberships;

    @Override @Transactional
    public CarrierCompanyResponse create(CarrierCompanyRequest request, User caller) {
        authenticated(caller);
        CarrierCompany company = new CarrierCompany();
        company.setOwnerId(caller.getId());
        company.setVerificationStatus(DRAFT);
        apply(request, company);
        CarrierCompanyResponse response = save(company);
        var owner = new com.ethioloadai.carrier.team.entity.CompanyMembership();
        owner.setCarrierCompanyId(company.getId());
        owner.setUserId(caller.getId());
        owner.setRole(com.ethioloadai.carrier.team.entity.CompanyRole.CARRIER_OWNER);
        owner.setStatus(com.ethioloadai.carrier.team.entity.MembershipStatus.ACTIVE);
        owner.setJoinedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        memberships.saveAndFlush(owner);
        return response;
    }

    @Override
    public Page<CarrierCompanyResponse> mine(User caller, int page, int size) {
        authenticated(caller);
        return companies.findByOwnerId(caller.getId(), paging(page, size)).map(mapper::toResponse);
    }

    @Override
    public CarrierCompanyResponse get(Long id, User caller) {
        return mapper.toResponse(readable(id, caller));
    }

    @Override @Transactional
    public CarrierCompanyResponse update(Long id, CarrierCompanyRequest request, User caller) {
        CarrierCompany company = ownedForUpdate(id, caller);
        editable(company);
        apply(request, company);
        return save(company);
    }

    @Override @Transactional
    public CarrierCompanyResponse submit(Long id, User caller) {
        CarrierCompany company = ownedForUpdate(id, caller);
        editable(company);
        List<CarrierCompanyDocument> documents = documentRepository.findByCarrierCompanyIdOrderById(id);
        if (List.of(
                Objects.toString(company.getTinNumber(), ""),
                Objects.toString(company.getBusinessRegistrationNumber(), ""),
                Objects.toString(company.getPhone(), ""),
                Objects.toString(company.getEmail(), ""),
                Objects.toString(company.getBusinessAddress(), ""),
                Objects.toString(company.getCity(), "")).stream().anyMatch(String::isBlank)) {
            throw invalid("TIN, registration number, phone, email, business address and city are required to submit");
        }
        Set<CompanyDocumentType> supplied = new HashSet<>();
        documents.forEach(document -> supplied.add(document.getDocumentType()));
        if (!supplied.containsAll(REQUIRED_DOCUMENTS)) {
            throw invalid("Business license, business registration and TIN certificate documents are required");
        }
        company.setVerificationStatus(PENDING);
        company.setSubmittedAt(LocalDateTime.now());
        company.setReviewMessage(null);
        company.setReviewedAt(null);
        company.setReviewedBy(null);
        documents.forEach(document -> {
            document.setVerificationStatus(PENDING);
            document.setReviewMessage(null);
            document.setReviewedAt(null);
            document.setReviewedBy(null);
        });
        return save(company);
    }

    @Override @Transactional
    public CarrierCompanyDocumentResponse upload(Long id, CompanyDocumentType type, MultipartFile file, User caller) {
        // All writes lock the parent first, serializing uploads, edits, submissions and decisions.
        CarrierCompany company = ownedForUpdate(id, caller);
        editable(company);
        String contentType = validateFile(type, file);
        CarrierCompanyDocument document = documentRepository.findByCarrierCompanyIdAndDocumentType(id, type)
            .orElseGet(CarrierCompanyDocument::new);
        String previousKey = document.getStorageKey();
        String key = "carrier-companies/" + id + "/" + UUID.randomUUID() + FILE_TYPES.get(contentType);
        // Storage is not transactional. Keep the old object until commit and remove new objects on rollback.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED) {
                        if (previousKey != null) deleteQuietly(previousKey);
                    } else {
                        deleteQuietly(key);
                    }
                }
            });
        }
        try {
            storage.put(key, file);
            document.setCarrierCompanyId(id);
            document.setDocumentType(type);
            document.setStorageKey(key);
            document.setOriginalName("document" + FILE_TYPES.get(contentType));
            document.setContentType(contentType);
            document.setFileSize(file.getSize());
            document.setUploadedAt(LocalDateTime.now());
            document.setVerificationStatus(DRAFT);
            document.setReviewMessage(null);
            document.setReviewedAt(null);
            document.setReviewedBy(null);
            return mapper.toResponse(documentRepository.saveAndFlush(document));
        } catch (IOException | RuntimeException ex) {
            deleteQuietly(key);
            throw new CarrierCompanyException("Unable to store company document", "CARRIER_STORAGE_ERROR", 503);
        }
    }

    @Override
    public List<CarrierCompanyDocumentResponse> documents(Long id, User caller) {
        readable(id, caller);
        return documentRepository.findByCarrierCompanyIdOrderById(id).stream().map(mapper::toResponse).toList();
    }

    @Override
    public DocumentStorage.StoredDocument download(Long id, Long documentId, User caller) {
        readable(id, caller);
        CarrierCompanyDocument document = documentRepository.findByIdAndCarrierCompanyId(documentId, id)
            .orElseThrow(CarrierCompanyException::missing);
        try {
            return storage.get(document.getStorageKey(), document.getOriginalName(), document.getContentType());
        } catch (RuntimeException ex) {
            throw new CarrierCompanyException("Company document is temporarily unavailable", "CARRIER_STORAGE_ERROR", 503);
        }
    }

    @Override
    public Page<CarrierCompanyResponse> applications(VerificationStatus status, int page, int size, User caller) {
        admin(caller);
        Pageable pageable = paging(page, size);
        return (status == null ? companies.findAll(pageable)
            : companies.findByVerificationStatus(status, pageable)).map(mapper::toResponse);
    }

    @Override @Transactional
    public CarrierCompanyResponse review(Long id, VerificationStatus decision, String message, User caller) {
        admin(caller);
        CarrierCompany company = companies.findForUpdate(id).orElseThrow(CarrierCompanyException::missing);
        if (company.getOwnerId().equals(caller.getId())) {
            throw new AuthorizationException("Owners cannot review their own company");
        }
        if (company.getVerificationStatus() != PENDING) throw invalid("Only PENDING applications can be reviewed");
        if (decision != APPROVED && decision != REJECTED && decision != MORE_INFORMATION_REQUIRED) {
            throw invalid("Invalid review decision");
        }
        String reason = clean(message);
        if (decision != APPROVED && reason == null) throw invalid("A review message is required");
        if (reason != null && reason.length() > 2000) throw invalid("Review message must be at most 2000 characters");
        LocalDateTime now = LocalDateTime.now();
        company.setVerificationStatus(decision);
        company.setReviewMessage(reason);
        company.setReviewedBy(caller.getId());
        company.setReviewedAt(now);
        // Phase 1 reviews the submitted company and its documents as a single application.
        documentRepository.findByCarrierCompanyIdOrderById(id).forEach(document -> {
            document.setVerificationStatus(decision);
            document.setReviewMessage(reason);
            document.setReviewedBy(caller.getId());
            document.setReviewedAt(now);
        });
        return save(company);
    }

    private CarrierCompany ownedForUpdate(Long id, User caller) {
        authenticated(caller);
        CarrierCompany company = companies.findForUpdate(id).orElseThrow(CarrierCompanyException::missing);
        if (!company.getOwnerId().equals(caller.getId())) throw missing();
        return company;
    }

    private CarrierCompany readable(Long id, User caller) {
        authenticated(caller);
        CarrierCompany company = companies.findById(id).orElseThrow(CarrierCompanyException::missing);
        // Admins may read any company for review purposes.
        if (caller.getRole() == User.Role.ADMIN) return company;
        // The owner may always read their own company.
        if (company.getOwnerId().equals(caller.getId())) return company;
        // Phase 3 fix (DEFECT-1): active company members (FLEET_MANAGER, DISPATCHER)
        // must be able to read their own company's data. An inactive/removed membership
        // does not grant access.
        boolean isActiveMember = memberships
                .findByCarrierCompanyIdAndUserId(id, caller.getId())
                .filter(m -> m.getStatus() == com.ethioloadai.carrier.team.entity.MembershipStatus.ACTIVE)
                .isPresent();
        if (!isActiveMember) throw missing();
        return company;
    }

    private void editable(CarrierCompany company) {
        if (company.getVerificationStatus() != DRAFT && company.getVerificationStatus() != MORE_INFORMATION_REQUIRED) {
            throw invalid("Only DRAFT or MORE_INFORMATION_REQUIRED applications are editable");
        }
    }

    private void apply(CarrierCompanyRequest request, CarrierCompany company) {
        // Check before mutating a managed entity: a repository query can trigger an automatic flush.
        String tin = identifier(request.getTinNumber());
        String registration = identifier(request.getBusinessRegistrationNumber());
        Long excludedId = company.getId() == null ? -1L : company.getId();
        if ((tin != null && companies.existsByTinNumberAndIdNot(tin, excludedId))
            || (registration != null && companies.existsByBusinessRegistrationNumberAndIdNot(registration, excludedId))) {
            throw duplicate();
        }
        mapper.update(request, company);
        company.setCompanyName(clean(company.getCompanyName()));
        company.setLegalName(clean(company.getLegalName()));
        company.setTinNumber(tin);
        company.setBusinessRegistrationNumber(registration);
        company.setPhone(clean(company.getPhone()));
        company.setEmail(clean(company.getEmail()));
        company.setBusinessAddress(clean(company.getBusinessAddress()));
        company.setCity(clean(company.getCity()));
    }

    private CarrierCompanyResponse save(CarrierCompany company) {
        try {
            return mapper.toResponse(companies.saveAndFlush(company));
        } catch (DataIntegrityViolationException ex) {
            // The database unique constraints also protect simultaneous registrations.
            throw duplicate();
        }
    }

    private Pageable paging(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw invalid("Page must be >= 0 and size must be between 1 and 100");
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    private void authenticated(User caller) {
        if (caller == null || caller.getId() == null) {
            throw new CarrierCompanyException("Authentication required", "UNAUTHENTICATED", 401);
        }
    }

    private void admin(User caller) {
        authenticated(caller);
        if (caller.getRole() != User.Role.ADMIN) throw new AuthorizationException("Admin access required");
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private String identifier(String value) {
        String normalized = clean(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String validateFile(CompanyDocumentType type, MultipartFile file) {
        if (type == null || file == null || file.isEmpty()) throw invalid("Document type and file are required");
        if (file.getSize() > 5L * 1024 * 1024) throw invalid("Document must be 5 MB or smaller");
        String contentType = Objects.toString(file.getContentType(), "").toLowerCase(Locale.ROOT);
        if (!FILE_TYPES.containsKey(contentType)) throw invalid("Only PDF, JPEG and PNG documents are accepted");
        try (var input = file.getInputStream()) {
            byte[] header = input.readNBytes(8);
            boolean valid = switch (contentType) {
                case "application/pdf" -> header.length >= 5 && header[0] == '%' && header[1] == 'P'
                    && header[2] == 'D' && header[3] == 'F' && header[4] == '-';
                case "image/jpeg" -> header.length >= 3 && (header[0] & 255) == 255
                    && (header[1] & 255) == 216 && (header[2] & 255) == 255;
                case "image/png" -> Arrays.equals(header, new byte[]{(byte)137, 80, 78, 71, 13, 10, 26, 10});
                default -> false;
            };
            if (!valid) throw invalid("File content does not match its declared type");
        } catch (IOException ex) {
            throw invalid("Unable to read document content");
        }
        return contentType;
    }

    private void deleteQuietly(String key) {
        try { storage.delete(key); }
        catch (RuntimeException ex) { log.warn("Company document cleanup failed; storage reconciliation required"); }
    }
}
