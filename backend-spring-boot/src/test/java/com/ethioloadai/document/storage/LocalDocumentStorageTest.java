package com.ethioloadai.document.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDocumentStorageTest {
    @TempDir
    Path root;

    @Test
    void storesRetrievesChecksAndDeletesPrivateObject() throws Exception {
        LocalDocumentStorage storage = new LocalDocumentStorage(root);
        MockMultipartFile file = new MockMultipartFile(
                "file", "private.pdf", "application/pdf", "%PDF-1.7".getBytes());
        String key = "driver-documents/42/license/random.pdf";

        storage.put(key, file);

        assertThat(storage.exists(key)).isTrue();
        assertThat(Files.readString(root.resolve(key))).isEqualTo("%PDF-1.7");
        var stored = storage.get(key, "private.pdf", "application/pdf");
        assertThat(stored.filename()).isEqualTo("private.pdf");
        storage.delete(key);
        assertThat(storage.exists(key)).isFalse();
    }

    @Test
    void rejectsTraversalAndReportsMissingObjects() {
        LocalDocumentStorage storage = new LocalDocumentStorage(root);

        assertThatThrownBy(() -> storage.exists("../outside.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.get("missing.pdf", "missing.pdf", "application/pdf"))
                .isInstanceOf(DocumentStorageObjectNotFoundException.class);
    }
}
