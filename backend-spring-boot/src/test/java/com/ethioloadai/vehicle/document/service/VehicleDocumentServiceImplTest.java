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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
}
