package com.ethioloadai.document.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.core.io.ByteArrayResource;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class LegacyDocumentStorageTest {
    @TempDir
    Path legacyRoot;

    @Test
    void existingLocalObjectRemainsReadableWhenB2DoesNotContainIt() throws Exception {
        DocumentStorage b2 = mock(DocumentStorage.class);
        LocalDocumentStorage legacy = new LocalDocumentStorage(legacyRoot);
        String key = "driver-documents/7/license/legacy.pdf";
        Files.createDirectories(legacyRoot.resolve("driver-documents/7/license"));
        Files.writeString(legacyRoot.resolve(key), "legacy content");
        when(b2.get(key, "license.pdf", "application/pdf"))
                .thenThrow(new DocumentStorageObjectNotFoundException(key));
        LegacyDocumentStorage storage = new LegacyDocumentStorage(b2, legacy);

        var document = storage.get(key, "license.pdf", "application/pdf");

        assertThat(document.resource().getInputStream().readAllBytes())
                .isEqualTo("legacy content".getBytes());
        assertThat(storage.exists(key)).isTrue();
        verify(b2).get(key, "license.pdf", "application/pdf");
    }

    @Test
    void providerFailureDoesNotFallBackToLocalOrHideOutage() {
        DocumentStorage b2 = mock(DocumentStorage.class);
        DocumentStorage legacy = mock(DocumentStorage.class);
        when(b2.get(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("B2 unavailable"));
        LegacyDocumentStorage storage = new LegacyDocumentStorage(b2, legacy);

        assertThatThrownBy(() -> storage.get("private/object.pdf", "object.pdf", "application/pdf"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("B2 unavailable");
        verifyNoInteractions(legacy);
    }

    @Test
    void ambiguousProvider404DoesNotFallBackToLocal() {
        DocumentStorage b2 = mock(DocumentStorage.class);
        DocumentStorage legacy = mock(DocumentStorage.class);
        RuntimeException provider404 = (S3Exception) S3Exception.builder()
                .statusCode(404).message("Unclassified provider 404").build();
        when(b2.get(anyString(), anyString(), anyString())).thenThrow(provider404);
        LegacyDocumentStorage storage = new LegacyDocumentStorage(b2, legacy);

        assertThatThrownBy(() -> storage.get("private/object.pdf", "object.pdf", "application/pdf"))
                .isSameAs(provider404);
        verifyNoInteractions(legacy);
    }

    @Test
    void newWritesGoOnlyToB2AndDeletesRemoveBothCopies() throws Exception {
        DocumentStorage b2 = mock(DocumentStorage.class);
        DocumentStorage legacy = mock(DocumentStorage.class);
        LegacyDocumentStorage storage = new LegacyDocumentStorage(b2, legacy);
        MockMultipartFile file = new MockMultipartFile("file", "new.pdf", "application/pdf", "data".getBytes());

        storage.put("driver-documents/7/license/new.pdf", file);
        storage.delete("driver-documents/7/license/old.pdf");

        verify(b2).put("driver-documents/7/license/new.pdf", file);
        verify(b2).delete("driver-documents/7/license/old.pdf");
        verify(legacy).delete("driver-documents/7/license/old.pdf");
        verifyNoMoreInteractions(legacy);
    }
}
