package com.ethioloadai.vehicle.document.repository;

import com.ethioloadai.vehicle.document.entity.VehicleDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleDocumentRepository extends JpaRepository<VehicleDocument, Long> {

    List<VehicleDocument> findByVehicle_IdOrderByCreatedAtDesc(Long vehicleId);

    Optional<VehicleDocument> findByVehicle_IdAndDocumentType(Long vehicleId, VehicleDocument.DocumentType documentType);

    List<VehicleDocument> findAllByOrderByCreatedAtDesc();

    List<VehicleDocument> findByStatusOrderByCreatedAtDesc(VehicleDocument.DocumentStatus status);

    long countByVehicle_IdAndStatus(Long vehicleId, VehicleDocument.DocumentStatus status);

    long countByVehicle_IdAndStatusAndDocumentTypeIn(
        Long vehicleId,
        VehicleDocument.DocumentStatus status,
        Collection<VehicleDocument.DocumentType> documentTypes
    );

    default boolean allRequiredApproved(Long vehicleId, Collection<VehicleDocument.DocumentType> requiredTypes) {
        return countByVehicle_IdAndStatusAndDocumentTypeIn(vehicleId, VehicleDocument.DocumentStatus.APPROVED, requiredTypes)
            >= requiredTypes.size();
    }
}
