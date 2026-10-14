package com.ethioloadai.vehicle.document.service;

import com.ethioloadai.exception.AuthorizationException;
import com.ethioloadai.exception.ValidationException;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import com.ethioloadai.vehicle.document.dto.VehicleDocumentResponse;
import com.ethioloadai.vehicle.document.dto.VehicleDocumentReviewRequest;
import com.ethioloadai.vehicle.document.entity.VehicleDocument;
import com.ethioloadai.vehicle.document.repository.VehicleDocumentRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import com.ethioloadai.document.storage.DocumentStorage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VehicleDocumentServiceImpl implements VehicleDocumentService {

    private static final Logger log = LoggerFactory.getLogger(VehicleDocumentServiceImpl.class);

    private static final long MAX_FILE_SIZE = 5L * 1024L * 1024L;
    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
        "application/pdf", ".pdf",
        "image/jpeg", ".jpg",
        "image/jpg", ".jpg",
        "image/png", ".png"
    );
    private static final Set<VehicleDocument.DocumentType> REQUIRED_DOCUMENT_TYPES = EnumSet.of(
        VehicleDocument.DocumentType.REGISTRATION_CERTIFICATE,
        VehicleDocument.DocumentType.INSURANCE_CERTIFICATE,
        VehicleDocument.DocumentType.ROADWORTHINESS_CERTIFICATE,
        VehicleDocument.DocumentType.OWNERSHIP_DOCUMENT
    );

    private final VehicleDocumentRepository vehicleDocumentRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final DocumentStorage documentStorage;

    @Override
    @Transactional(readOnly = true)
    public List<VehicleDocumentResponse> getDocumentsForVehicle(Long vehicleId, User callerUser) {
        Vehicle vehicle = resolveVehicle(vehicleId);
        enforceVehicleAccess(vehicle, callerUser);
        return vehicleDocumentRepository.findByVehicle_IdOrderByCreatedAtDesc(vehicleId)
            .stream()
            .map(VehicleDocumentResponse::from)
            .toList();
    }

    @Override
    @Transactional
    public VehicleDocumentResponse uploadDocument(Long vehicleId, VehicleDocument.DocumentType documentType, MultipartFile file, User callerUser) {
        if (documentType == null) {
            throw new ValidationException("Document type is required", "documentType", "Document type is required");
        }
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Document file is required", "file", "Document file is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ValidationException("Document must be 5 MB or smaller", "file", "Document must be 5 MB or smaller");
        }

        String contentType = file.getContentType() == null ? "" : file.getContentType().trim().toLowerCase(Locale.ROOT);
        String extension = ALLOWED_CONTENT_TYPES.get(contentType);
        if (extension == null) {
            throw new ValidationException("Only PDF, JPG, and PNG documents are accepted", "file", "Only PDF, JPG, and PNG documents are accepted");
        }
        try {
            if (!hasExpectedSignature(file, contentType)) {
                throw new ValidationException("File content does not match its declared type", "file", "File content does not match its declared type");
            }
        } catch (IOException e) {
            throw new ValidationException("Unable to read document content", "file", "Unable to read document content");
        }

        Vehicle vehicle = resolveVehicle(vehicleId);
        enforceVehicleAccess(vehicle, callerUser);

        VehicleDocument existing = vehicleDocumentRepository.findByVehicle_IdAndDocumentType(vehicleId, documentType).orElse(null);
        String previousObjectKey = existing != null ? existing.getFilePath() : null;
        String objectKey = "vehicle-documents/" + vehicleId + "/" + documentType.name().toLowerCase(Locale.ROOT) + "/" + UUID.randomUUID() + extension;

        try {
            documentStorage.put(objectKey, file);
        } catch (IOException e) {
            throw new ValidationException("Document storage failed", "file", "Document storage failed");
        }

        try {
            VehicleDocument target = existing != null ? existing : new VehicleDocument();
            target.setVehicle(vehicle);
            target.setDocumentType(documentType);
            target.setDocumentLabel(documentType.getLabel());
            target.setFilePath(objectKey);
            target.setOriginalName(file.getOriginalFilename() == null || file.getOriginalFilename().isBlank() ? objectKey : file.getOriginalFilename());
            target.setMimeType(contentType);
            target.setFileSize(file.getSize());
            target.setStatus(VehicleDocument.DocumentStatus.PENDING);
            target.setRejectionReason(null);
            target.setReviewedBy(null);
            target.setReviewedAt(null);

            VehicleDocument saved = vehicleDocumentRepository.save(target);
            if (previousObjectKey != null && !previousObjectKey.equals(objectKey)) {
                try {
                    documentStorage.delete(previousObjectKey);
                } catch (RuntimeException ignored) {
                    log.warn("Previous vehicle document storage object could not be removed after replacement: {}", previousObjectKey);
                }
            }
            return VehicleDocumentResponse.from(saved);
        } catch (RuntimeException ex) {
            try {
                documentStorage.delete(objectKey);
            } catch (RuntimeException ignored) {
                log.warn("Vehicle document upload failed and cleanup could not remove the newly uploaded object: {}", objectKey);
            }
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleDocumentResponse> getAllDocuments(User adminUser, VehicleDocument.DocumentStatus status) {
        ensureAdmin(adminUser);
        List<VehicleDocument> documents;
        if (status == null) {
            documents = vehicleDocumentRepository.findAllByOrderByCreatedAtDesc();
        } else {
            documents = vehicleDocumentRepository.findByStatusOrderByCreatedAtDesc(status);
        }
        return documents.stream().map(VehicleDocumentResponse::from).toList();
    }

    @Override
    @Transactional
    public VehicleDocumentResponse reviewDocument(Long documentId, VehicleDocumentReviewRequest request, User adminUser) {
        ensureAdmin(adminUser);
        if (request == null || request.getStatus() == null || request.getStatus().isBlank()) {
            throw new ValidationException("Review action is required", "status", "Review action is required");
        }

        VehicleDocument.DocumentStatus nextStatus = parseReviewStatus(request.getStatus());
        if (nextStatus == VehicleDocument.DocumentStatus.REJECTED &&
            (request.getRejectionReason() == null || request.getRejectionReason().isBlank())) {
            throw new ValidationException("Rejection reason is required when rejecting a document", "rejectionReason", "Rejection reason is required when rejecting a document");
        }

        VehicleDocument document = vehicleDocumentRepository.findById(documentId)
            .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("Vehicle document"));

        document.setStatus(nextStatus);
        document.setReviewedBy(adminUser);
        document.setReviewedAt(LocalDateTime.now());
        document.setRejectionReason(nextStatus == VehicleDocument.DocumentStatus.REJECTED ? request.getRejectionReason() : null);

        VehicleDocument saved = vehicleDocumentRepository.save(document);
        updateVehicleVerificationState(document.getVehicle().getId());
        return VehicleDocumentResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentFile getDocumentFile(Long documentId, User callerUser) {
        VehicleDocument document = vehicleDocumentRepository.findById(documentId)
            .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("Vehicle document"));
        Vehicle vehicle = document.getVehicle();
        if (vehicle == null) {
            throw new AuthorizationException.ResourceNotFoundException("Vehicle document");
        }
        if (!isAdmin(callerUser) && !vehicle.getUserId().equals(callerUser.getId()) &&
            (vehicle.getFleetOwnerId() == null || !vehicle.getFleetOwnerId().equals(callerUser.getId()))) {
            throw new AuthorizationException.AccessDeniedException();
        }

        try {
            DocumentStorage.StoredDocument storedDocument = documentStorage.get(document.getFilePath(), document.getOriginalName(), document.getMimeType());
            return new DocumentFile(storedDocument.resource(), storedDocument.filename(), storedDocument.contentType());
        } catch (IllegalStateException e) {
            throw new AuthorizationException.ResourceNotFoundException("Vehicle document file");
        }
    }

    private void enforceVehicleAccess(Vehicle vehicle, User callerUser) {
        if (isAdmin(callerUser)) {
            return;
        }
        if (!vehicle.getUserId().equals(callerUser.getId()) &&
            (vehicle.getFleetOwnerId() == null || !vehicle.getFleetOwnerId().equals(callerUser.getId()))) {
            throw new AuthorizationException.AccessDeniedException();
        }
    }

    private void ensureAdmin(User user) {
        if (!isAdmin(user)) {
            throw new AuthorizationException.AccessDeniedException();
        }
    }

    private boolean isAdmin(User user) {
        return user != null && user.getRole() == User.Role.ADMIN;
    }

    private Vehicle resolveVehicle(Long vehicleId) {
        return vehicleRepository.findById(vehicleId)
            .orElseThrow(() -> new AuthorizationException.ResourceNotFoundException("Vehicle"));
    }

    private void updateVehicleVerificationState(Long vehicleId) {
        Vehicle vehicle = resolveVehicle(vehicleId);
        long approvedCount = vehicleDocumentRepository.countByVehicle_IdAndStatusAndDocumentTypeIn(
            vehicleId,
            VehicleDocument.DocumentStatus.APPROVED,
            REQUIRED_DOCUMENT_TYPES
        );
        if (approvedCount >= REQUIRED_DOCUMENT_TYPES.size()) {
            if (vehicle.getApprovalStatus() != Vehicle.VehicleApprovalStatus.APPROVED) {
                vehicle.setApprovalStatus(Vehicle.VehicleApprovalStatus.APPROVED);
                vehicleRepository.save(vehicle);
            }
            return;
        }
        if (vehicle.getApprovalStatus() == Vehicle.VehicleApprovalStatus.APPROVED) {
            vehicle.setApprovalStatus(Vehicle.VehicleApprovalStatus.UNDER_REVIEW);
            vehicleRepository.save(vehicle);
        }
    }

    private boolean hasExpectedSignature(MultipartFile file, String contentType) throws IOException {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(8);
            if ("application/pdf".equals(contentType)) {
                return header.length >= 5 && header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F' && header[4] == '-';
            }
            if ("image/png".equals(contentType)) {
                return header.length >= 8 && (header[0] & 0xff) == 0x89 && header[1] == 0x50 && header[2] == 0x4e && header[3] == 0x47 && header[4] == 0x0d && header[5] == 0x0a && header[6] == 0x1a && header[7] == 0x0a;
            }
            return header.length >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8 && (header[2] & 0xff) == 0xff;
        }
    }

    private VehicleDocument.DocumentStatus parseReviewStatus(String rawStatus) {
        String normalized = rawStatus.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if ("approve".equals(normalized) || "approved".equals(normalized)) {
            return VehicleDocument.DocumentStatus.APPROVED;
        }
        if ("reject".equals(normalized) || "rejected".equals(normalized)) {
            return VehicleDocument.DocumentStatus.REJECTED;
        }
        throw new ValidationException("Unsupported review status: " + rawStatus, "status", "Unsupported review status");
    }
}
