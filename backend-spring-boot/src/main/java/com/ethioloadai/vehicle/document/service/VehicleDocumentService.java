package com.ethioloadai.vehicle.document.service;

import com.ethioloadai.user.entity.User;
import com.ethioloadai.vehicle.document.dto.VehicleDocumentResponse;
import com.ethioloadai.vehicle.document.dto.VehicleDocumentReviewRequest;
import com.ethioloadai.vehicle.document.entity.VehicleDocument;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface VehicleDocumentService {

    record DocumentFile(Resource resource, String filename, String contentType) {}

    List<VehicleDocumentResponse> getDocumentsForVehicle(Long vehicleId, User callerUser);

    VehicleDocumentResponse uploadDocument(Long vehicleId, VehicleDocument.DocumentType documentType, MultipartFile file, User callerUser);

    List<VehicleDocumentResponse> getAllDocuments(User adminUser, VehicleDocument.DocumentStatus status);

    VehicleDocumentResponse reviewDocument(Long documentId, VehicleDocumentReviewRequest request, User adminUser);

    DocumentFile getDocumentFile(Long documentId, User callerUser);
}
