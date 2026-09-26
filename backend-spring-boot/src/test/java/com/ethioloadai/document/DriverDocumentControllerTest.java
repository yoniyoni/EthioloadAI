package com.ethioloadai.document;

import com.ethioloadai.document.controller.DriverDocumentController;
import com.ethioloadai.document.dto.DocumentResponse;
import com.ethioloadai.document.dto.DocumentReviewRequest;
import com.ethioloadai.document.entity.DriverDocument;
import com.ethioloadai.document.service.DriverDocumentService;
import com.ethioloadai.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DriverDocumentController")
class DriverDocumentControllerTest {

    @Mock
    private DriverDocumentService documentService;

    @InjectMocks
    private DriverDocumentController controller;

    private User admin;
    private User driver;

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setId(1L);
        admin.setRole(User.Role.ADMIN);

        driver = new User();
        driver.setId(2L);
        driver.setRole(User.Role.DRIVER);
    }

    @Test
    @DisplayName("GET /api/documents returns 200 with list of documents")
    void getAllDocuments_returns200Ok() {
        List<DocumentResponse> mockDocs = List.of(
            DocumentResponse.builder()
                .id(10L)
                .userId(2L)
                .driverName("Abebe")
                .documentType("LICENSE")
                .status("PENDING")
                .build()
        );

        when(documentService.getAllDocuments(eq(admin), any())).thenReturn(mockDocs);

        ResponseEntity<List<DocumentResponse>> response = controller.getAllDocuments(null, admin);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getDriverName()).isEqualTo("Abebe");
        verify(documentService).getAllDocuments(admin, null);
    }

    @Test
    @DisplayName("GET /api/documents/{id}/file streams document file with inline disposition")
    void getDocumentFile_returns200WithResource() {
        ByteArrayResource resource = new ByteArrayResource("test file content".getBytes());
        DriverDocumentService.DocumentFile docFile = new DriverDocumentService.DocumentFile(
            resource, "license.pdf", "application/pdf"
        );

        when(documentService.getDocumentFile(10L, admin)).thenReturn(docFile);

        ResponseEntity<Resource> response = controller.getDocumentFile(10L, admin);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
        assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("inline");
        assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("license.pdf");
        assertThat(response.getBody()).isEqualTo(resource);
        verify(documentService).getDocumentFile(10L, admin);
    }

    @Test
    @DisplayName("POST /api/documents/{id}/review returns 200 with reviewed document")
    void reviewDocument_returns200Ok() {
        DocumentReviewRequest req = new DocumentReviewRequest();
        req.setStatus("approved");

        DocumentResponse mockResponse = DocumentResponse.builder()
            .id(10L)
            .userId(2L)
            .status("APPROVED")
            .build();

        when(documentService.reviewDocument(eq(10L), any(DocumentReviewRequest.class), eq(admin)))
            .thenReturn(mockResponse);

        ResponseEntity<DocumentResponse> response = controller.reviewDocument(10L, req, admin);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("APPROVED");
        verify(documentService).reviewDocument(eq(10L), any(DocumentReviewRequest.class), eq(admin));
    }

    @Test
    @DisplayName("Security: endpoints enforce expected authorization rules")
    void securityAnnotations_correctlyConfigured() throws NoSuchMethodException {
        Method getAllDocs = DriverDocumentController.class.getMethod("getAllDocuments", DriverDocument.DocumentStatus.class, User.class);
        PreAuthorize getAllAuth = getAllDocs.getAnnotation(PreAuthorize.class);
        assertThat(getAllAuth).isNotNull();
        assertThat(getAllAuth.value()).isEqualTo("hasRole('ADMIN')");

        Method review = DriverDocumentController.class.getMethod("reviewDocument", Long.class, DocumentReviewRequest.class, User.class);
        PreAuthorize reviewAuth = review.getAnnotation(PreAuthorize.class);
        assertThat(reviewAuth).isNotNull();
        assertThat(reviewAuth.value()).isEqualTo("hasRole('ADMIN')");

        Method getFile = DriverDocumentController.class.getMethod("getDocumentFile", Long.class, User.class);
        PreAuthorize fileAuth = getFile.getAnnotation(PreAuthorize.class);
        assertThat(fileAuth).isNotNull();
        assertThat(fileAuth.value()).isEqualTo("hasAnyRole('DRIVER','ADMIN')");
    }
}
