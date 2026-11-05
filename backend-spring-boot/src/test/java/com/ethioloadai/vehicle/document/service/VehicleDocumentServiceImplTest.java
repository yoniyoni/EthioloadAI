package com.ethioloadai.vehicle.document.service;

import com.ethioloadai.document.storage.DocumentStorage;
import com.ethioloadai.exception.AuthorizationException;
import com.ethioloadai.vehicle.document.dto.VehicleDocumentReviewRequest;
import com.ethioloadai.vehicle.document.entity.VehicleDocument;
import com.ethioloadai.vehicle.document.repository.VehicleDocumentRepository;
import com.ethioloadai.vehicle.entity.Vehicle;
import com.ethioloadai.vehicle.repository.VehicleRepository;
import com.ethioloadai.user.entity.User;
import com.ethioloadai.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleDocumentServiceImplTest {

    @Mock
    private VehicleDocumentRepository documentRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DocumentStorage documentStorage;

    private VehicleDocumentServiceImpl service;
    private Vehicle vehicle;
    private VehicleDocument document;
    private User admin;

    @BeforeEach
    void setUp() {
        service = new VehicleDocumentServiceImpl(
            documentRepository, vehicleRepository, userRepository, documentStorage);

        vehicle = new Vehicle();
        vehicle.setId(20L);
        vehicle.setUserId(30L);
        vehicle.setPlateNumber("TEST-PLATE");
        vehicle.setApprovalStatus(Vehicle.VehicleApprovalStatus.UNDER_REVIEW);

        document = new VehicleDocument();
        document.setId(40L);
        document.setVehicle(vehicle);
        document.setDocumentType(VehicleDocument.DocumentType.REGISTRATION_CERTIFICATE);
        document.setDocumentLabel("Registration Certificate");
        document.setOriginalName("registration.pdf");
        document.setMimeType("application/pdf");
        document.setFileSize(10L);

        admin = new User();
        admin.setId(1L);
        admin.setRole(User.Role.ADMIN);

    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void reviewApprovalApprovesVehicleOnlyAfterEveryRequiredDocumentIsApproved() {
        when(documentRepository.findById(40L)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(VehicleDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle));
        when(documentRepository.countByVehicle_IdAndStatusAndDocumentTypeIn(
            eq(20L), eq(VehicleDocument.DocumentStatus.APPROVED), any()))
            .thenReturn(4L);
        VehicleDocumentReviewRequest request = new VehicleDocumentReviewRequest();
        request.setStatus("approved");

        var response = service.reviewDocument(40L, request, admin);

        assertThat(response.getStatus()).isEqualTo("approved");
        assertThat(document.getReviewedBy()).isSameAs(admin);
        assertThat(document.getReviewedAt()).isNotNull();
        assertThat(vehicle.getApprovalStatus()).isEqualTo(Vehicle.VehicleApprovalStatus.APPROVED);
        verify(vehicleRepository).save(vehicle);
    }

    @Test
    void reviewApprovalKeepsVehicleUnderReviewWhileRequiredDocumentsAreMissing() {
        when(documentRepository.findById(40L)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(VehicleDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle));
        when(documentRepository.countByVehicle_IdAndStatusAndDocumentTypeIn(
            eq(20L), eq(VehicleDocument.DocumentStatus.APPROVED), any()))
            .thenReturn(3L);
        VehicleDocumentReviewRequest request = new VehicleDocumentReviewRequest();
        request.setStatus("approve");

        service.reviewDocument(40L, request, admin);

        assertThat(document.getStatus()).isEqualTo(VehicleDocument.DocumentStatus.APPROVED);
        assertThat(vehicle.getApprovalStatus()).isEqualTo(Vehicle.VehicleApprovalStatus.UNDER_REVIEW);
        verify(vehicleRepository, never()).save(any(Vehicle.class));
    }

    @Test
    void nonAdminCannotReviewVehicleDocuments() {
        User fleetOwner = new User();
        fleetOwner.setId(2L);
        fleetOwner.setRole(User.Role.FLEET_OWNER);
        VehicleDocumentReviewRequest request = new VehicleDocumentReviewRequest();
        request.setStatus("approved");

        assertThatThrownBy(() -> service.reviewDocument(40L, request, fleetOwner))
            .isInstanceOf(AuthorizationException.AccessDeniedException.class);

        verify(documentRepository, never()).findById(any());
    }

    @Test
    void replacementRetainsOldObjectUntilDatabaseCommit() throws Exception {
        User caller = new User();
        caller.setId(vehicle.getUserId());
        caller.setRole(User.Role.DRIVER);
        document.setFilePath("vehicle-documents/20/registration_certificate/previous.pdf");
        when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle));
        when(documentRepository.findByVehicle_IdAndDocumentType(
                20L, VehicleDocument.DocumentType.REGISTRATION_CERTIFICATE)).thenReturn(Optional.of(document));
        when(documentRepository.saveAndFlush(any(VehicleDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();

        service.uploadDocument(20L, VehicleDocument.DocumentType.REGISTRATION_CERTIFICATE, validPdf(), caller);

        String newKey = document.getFilePath();
        verify(documentStorage, never()).delete(document.getFilePath());
        verify(documentStorage, never()).delete("vehicle-documents/20/registration_certificate/previous.pdf");
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
        verify(documentStorage).delete("vehicle-documents/20/registration_certificate/previous.pdf");
        verify(documentStorage, never()).delete(newKey);
    }

    @Test
    void databaseFailureDeletesNewObjectAndPreservesPreviousObject() throws Exception {
        User caller = new User();
        caller.setId(vehicle.getUserId());
        caller.setRole(User.Role.DRIVER);
        String previousKey = "vehicle-documents/20/registration_certificate/previous.pdf";
        document.setFilePath(previousKey);
        when(vehicleRepository.findById(20L)).thenReturn(Optional.of(vehicle));
        when(documentRepository.findByVehicle_IdAndDocumentType(
                20L, VehicleDocument.DocumentType.REGISTRATION_CERTIFICATE)).thenReturn(Optional.of(document));
        when(documentRepository.saveAndFlush(any(VehicleDocument.class)))
                .thenThrow(new IllegalStateException("simulated database failure"));
        TransactionSynchronizationManager.initSynchronization();

        assertThatThrownBy(() -> service.uploadDocument(
                20L, VehicleDocument.DocumentType.REGISTRATION_CERTIFICATE, validPdf(), caller))
                .isInstanceOf(IllegalStateException.class);
        String newKey = document.getFilePath();
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(documentStorage).delete(newKey);
        verify(documentStorage, never()).delete(previousKey);
    }

    @Test
    void invalidAndOversizedVehicleUploadsNeverReachStorage() {
        User caller = new User();
        caller.setId(vehicle.getUserId());
        caller.setRole(User.Role.DRIVER);

        assertThatThrownBy(() -> service.uploadDocument(
                20L, VehicleDocument.DocumentType.REGISTRATION_CERTIFICATE,
                new MockMultipartFile("file", "bad.pdf", "application/pdf", "not a pdf".getBytes()), caller))
                .hasMessageContaining("content does not match");
        assertThatThrownBy(() -> service.uploadDocument(
                20L, VehicleDocument.DocumentType.REGISTRATION_CERTIFICATE,
                new MockMultipartFile("file", "large.pdf", "application/pdf", new byte[5 * 1024 * 1024 + 1]), caller))
                .hasMessageContaining("5 MB or smaller");

        verifyNoInteractions(documentStorage);
        verify(documentRepository, never()).saveAndFlush(any());
    }

    @Test
    void unauthorizedVehicleDocumentDownloadDoesNotReadStorage() {
        User other = new User();
        other.setId(99L);
        other.setRole(User.Role.DRIVER);
        when(documentRepository.findById(40L)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.getDocumentFile(40L, other))
                .isInstanceOf(AuthorizationException.AccessDeniedException.class);

        verifyNoInteractions(documentStorage);
    }

    private MockMultipartFile validPdf() {
        return new MockMultipartFile("file", "registration.pdf", "application/pdf",
                "%PDF-1.7 valid test".getBytes(StandardCharsets.US_ASCII));
    }

    private void completeTransaction(int status) {
        List<TransactionSynchronization> synchronizations =
                new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
        synchronizations.forEach(synchronization -> synchronization.afterCompletion(status));
    }
}
