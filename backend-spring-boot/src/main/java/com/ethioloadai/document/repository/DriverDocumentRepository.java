package com.ethioloadai.document.repository;

import com.ethioloadai.document.entity.DriverDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverDocumentRepository extends JpaRepository<DriverDocument, Long> {

    /** All documents for a driver (their KYC portfolio). */
    List<DriverDocument> findByUser_Id(Long userId);

    /** Single document by type for a driver (unique per type per driver). */
    Optional<DriverDocument> findByUser_IdAndDocumentType(
            Long userId, DriverDocument.DocumentType documentType);

    /** All documents pending admin review. */
    List<DriverDocument> findByStatus(DriverDocument.DocumentStatus status);

    /** All documents ordered by createdAt descending (admin view). */
    List<DriverDocument> findAllByOrderByCreatedAtDesc();

    /** Documents filtered by status ordered by createdAt descending (admin view). */
    List<DriverDocument> findByStatusOrderByCreatedAtDesc(DriverDocument.DocumentStatus status);

    /** Count of approved documents for a driver — used in activation gate. */
    long countByUser_IdAndStatus(Long userId, DriverDocument.DocumentStatus status);

    /**
     * Returns true when all 5 required document types are approved.
     * Used to determine if a driver should be activated.
     *
     * <p>Uses a count-based check via the derived query (which routes through the
     * AttributeConverter correctly) rather than a JPQL string literal, which would
     * be compared against the raw DB value and miss the lowercase conversion.
     */
    default boolean allDocumentsApproved(Long userId) {
        return countByUser_IdAndStatus(userId, DriverDocument.DocumentStatus.APPROVED) == 5;
    }

    /** Find a document and verify ownership (IDOR protection). */
    Optional<DriverDocument> findByIdAndUser_Id(Long documentId, Long userId);
}
